// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.regalloc;

import static com.android.tools.r8.ir.regalloc.LiveIntervals.NO_REGISTER;
import static com.android.tools.r8.utils.IntConsumerUtils.emptyIntConsumer;
import static com.google.common.base.Predicates.not;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.Argument;
import com.android.tools.r8.ir.code.ConstInstruction;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.FixedRegisterValue;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Move;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.utils.ObjectUtils;
import com.google.common.collect.Iterables;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntRBTreeSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.TreeSet;
import java.util.function.IntConsumer;

public class RegisterMoveScheduler {
  // The set of moves to schedule.
  private final TreeSet<RegisterMove> moveSet = new TreeSet<>();
  // Mapping to keep track of which values currently corresponds to each other.
  // This is initially an identity map but changes as we insert moves.
  private final Int2IntMap valueMap = new Int2IntOpenHashMap();
  // Location at which to insert the scheduled moves.
  private final InstructionListIterator insertAt;
  // Debug position associated with insertion point.
  private final Position position;
  // The first available temporary register.
  private final int firstTempRegister;
  private int nextTempRegister;
  private final int numberOfArgumentRegisters;
  // Free temporary registers that have been allocated by move scheduling.
  private final IntSortedSet freeRegisters = new IntRBTreeSet();
  // Registers that can be used as temporary registers until they are assigned by a move in the
  // move set.
  private final IntSortedSet freeRegistersUntilAssigned = new IntRBTreeSet();
  // Registers that are the destination register of some move in the move set, but which are
  // currently being used as a temporary register for another value. Moves in the move set that
  // write a register in this set are blocked until the temporary registers are released.
  public final IntSet activeTempRegisters = new IntOpenHashSet();

  public RegisterMoveScheduler(
      InstructionListIterator insertAt,
      int firstTempRegister,
      int numberOfArgumentRegisters,
      Position position) {
    this.insertAt = insertAt;
    this.firstTempRegister = firstTempRegister;
    this.nextTempRegister = firstTempRegister;
    this.numberOfArgumentRegisters = numberOfArgumentRegisters;
    this.position = position;
    this.valueMap.defaultReturnValue(NO_REGISTER);
  }

  public RegisterMoveScheduler(InstructionListIterator insertAt, int firstTempRegister) {
    this(insertAt, firstTempRegister, 0);
  }

  public RegisterMoveScheduler(
      InstructionListIterator insertAt, int firstTempRegister, int numberOfArgumentRegisters) {
    this(insertAt, firstTempRegister, numberOfArgumentRegisters, Position.none());
  }

  private void initializeFreeRegistersUntilAssigned() {
    // All registers that are assigned by the move set but not read can be used as temporary
    // registers until they are assigned.
    assert activeTempRegisters.isEmpty();
    assert freeRegistersUntilAssigned.isEmpty();
    for (RegisterMove move : moveSet) {
      move.forEachDestinationRegister(freeRegistersUntilAssigned::add);
    }
    for (RegisterMove move : moveSet) {
      move.forEachSourceRegister(freeRegistersUntilAssigned::remove);
    }
  }

  public void addMove(RegisterMove move) {
    moveSet.add(move);
    if (move.src != NO_REGISTER) {
      valueMap.put(move.src, move.src);
    }
    valueMap.put(move.dst, move.dst);
  }

  public void schedule() {
    assert everyDestinationOnlyWrittenOnce();
    initializeFreeRegistersUntilAssigned();

    // Worklist of moves that are ready to be inserted.
    Deque<RegisterMove> worklist = new ArrayDeque<>();

    // If there are destination registers we can use until they are assigned, then instead of
    // emitting these unblocked moves, we use them as temporary registers to unblock move cycles.
    List<RegisterMoveCycle> moveCycles = RegisterMoveCycleDetector.getMoveCycles(moveSet);
    for (RegisterMoveCycle moveCycle : moveCycles) {
      for (RegisterMove move : moveCycle.getMoves()) {
        assert move.isBlocked(this, moveSet, valueMap) || !moveSet.contains(move);
        removeFromFreeRegistersUntilAssigned(move.dst, move.isWide(), emptyIntConsumer());
      }
      // If the cycle is not closed then some moves in the cycle are blocked by other moves.
      // TODO(b/375147902): Try to schedule these other moves before scheduling the cycle to
      //  unblock the cycle. In JetNews 15% of move cycles are open.
      if (moveCycle.isClosed()) {
        assert moveSet.containsAll(moveCycle.getMoves());
        assert worklist.isEmpty();
        schedulePartial(moveCycle.getMoves(), worklist);
      }
      assert activeTempRegisters.isEmpty();
    }

    // Initialize worklist with the moves that do not interfere with other moves.
    enqueueUnblockedMoves(worklist);
    schedulePartial(moveSet, worklist);
    assert freeRegistersUntilAssigned.isEmpty();
  }

  private void schedulePartial(
      TreeSet<RegisterMove> movesToSchedule, Deque<RegisterMove> worklist) {
    // Process the worklist generating moves. If the worklist becomes empty while the move set
    // still contains elements we need to use a temporary to break cycles.
    while (!worklist.isEmpty() || !movesToSchedule.isEmpty()) {
      while (!worklist.isEmpty()) {
        while (!worklist.isEmpty()) {
          RegisterMove move = worklist.removeFirst();
          assert !move.isBlocked(this, moveSet, valueMap);
          createMove(move);
        }
        enqueueUnblockedMoves(worklist, movesToSchedule);
      }
      if (!movesToSchedule.isEmpty()) {
        // The remaining moves are conflicting. Chose a move and unblock it by generating moves to
        // temporary registers for its destination value(s).
        RegisterMove move = pickMoveToUnblock(movesToSchedule);
        createMoveDestToTemp(move);
        // TODO(b/375147902): After emitting the newly unblocked move, try to prioritize the moves
        //  that blocked it, so that we free up the temp register, rather than getting overlapping
        //  temporary registers.
        worklist.add(move);
      }
    }
  }

  public int getUsedTempRegisters() {
    return nextTempRegister - firstTempRegister;
  }

  private List<RegisterMove> findMovesWithSrc(int src, TypeElement type) {
    List<RegisterMove> result = new ArrayList<>();
    assert src != NO_REGISTER;
    for (RegisterMove move : moveSet) {
      if (move.src == NO_REGISTER) {
        continue;
      }
      int moveSrc = valueMap.get(move.src);
      if (moveSrc == src) {
        result.add(move);
      } else if (move.isWide() && (moveSrc + 1) == src) {
        result.add(move);
      } else if (type.isWidePrimitive() && (moveSrc - 1) == src) {
        result.add(move);
      }
    }
    return result;
  }

  private void createMove(RegisterMove move) {
    Instruction instruction;
    if (move.definition != null) {
      if (move.definition.isArgument()) {
        Argument argument = move.definition.asArgument();
        int argumentRegister = argument.outValue().getLiveIntervals().getRegister();
        Value to = new FixedRegisterValue(argument.getOutType(), move.dst);
        Value from = new FixedRegisterValue(argument.getOutType(), argumentRegister);
        instruction = new Move(to, from);
      } else {
        assert move.definition.isOutConstant();
        ConstInstruction definition = move.definition.getOutConstantConstInstruction();
        if (definition.isConstNumber()) {
          Value to = new FixedRegisterValue(move.definition.getOutType(), move.dst);
          instruction = new ConstNumber(to, definition.asConstNumber().getRawValue());
        } else {
          throw new Unreachable("Unexpected definition");
        }
      }
    } else {
      int mappedSrc = valueMap.get(move.src);
      Value to = new FixedRegisterValue(move.type, move.dst);
      Value from = new FixedRegisterValue(move.type, mappedSrc);
      instruction = new Move(to, from);
      returnTemporaryRegister(mappedSrc, move.isWide());
    }
    instruction.setPosition(position);
    insertAt.add(instruction);

    // Update the value map with the information that dest can be used instead of
    // src starting now.
    if (move.src != NO_REGISTER) {
      valueMap.put(move.src, move.dst);
    }
    removeFromFreeRegistersUntilAssigned(move.dst, move.isWide(), emptyIntConsumer());
  }

  private void enqueueUnblockedMoves(Deque<RegisterMove> worklist) {
    // Iterate and find the moves that were blocked because they need to write to one of the move
    // src. That is now valid because the move src is preserved in dest.
    moveSet.removeIf(
        move -> {
          if (move.isBlocked(this, moveSet, valueMap)) {
            return false;
          }
          worklist.addLast(move);
          return true;
        });
  }

  private void enqueueUnblockedMoves(
      Deque<RegisterMove> worklist, TreeSet<RegisterMove> movesToSchedule) {
    // Iterate and find the moves that were blocked because they need to write to one of the move
    // src. That is now valid because the move src is preserved in dest.
    movesToSchedule.removeIf(
        move -> {
          if (move.isBlocked(this, moveSet, valueMap)) {
            return false;
          }
          if (ObjectUtils.notIdentical(moveSet, movesToSchedule)) {
            moveSet.remove(move);
          }
          worklist.addLast(move);
          return true;
        });
  }

  private void createMoveDestToTemp(RegisterMove move) {
    // In order to unblock this move we might have to move more than one value to temporary
    // registers if we are unlucky with the overlap for values that use two registers.
    List<RegisterMove> movesWithSrc = findMovesWithSrc(move.dst, move.type);
    assert movesWithSrc.size() > 0;
    assert verifyMovesHaveDifferentSources(movesWithSrc);
    for (RegisterMove moveWithSrc : movesWithSrc) {
      // TODO(b/375147902): Maybe seed the move scheduler with a set of registers known to be free
      //  at this point.
      int register = takeFreeRegister(moveWithSrc.isWide());
      Value to = new FixedRegisterValue(moveWithSrc.type, register);
      Value from = new FixedRegisterValue(moveWithSrc.type, valueMap.get(moveWithSrc.src));
      Move instruction = new Move(to, from);
      instruction.setPosition(position);
      insertAt.add(instruction);
      valueMap.put(moveWithSrc.src, register);
    }
  }

  private int takeFreeRegister(boolean wide) {
    int register = takeFreeRegisterFrom(freeRegistersUntilAssigned, wide);
    if (register != NO_REGISTER) {
      addActiveTempRegisters(register, wide);
      return register;
    }
    register = takeFreeRegisterFrom(freeRegisters, wide);
    if (register != NO_REGISTER) {
      return register;
    }
    // We don't have a free register.
    register = allocateExtraRegister();
    if (!wide) {
      return register;
    }
    if (freeRegisters.remove(register - 1)) {
      return register - 1;
    }
    allocateExtraRegister();
    return register;
  }

  @SuppressWarnings("ModifyCollectionInEnhancedForLoop")
  private int takeFreeRegisterFrom(IntSortedSet freeRegisters, boolean wide) {
    for (int freeRegister : freeRegisters) {
      if (wide) {
        int freeRegisterEnd = freeRegister + 1;
        if (freeRegisterEnd == numberOfArgumentRegisters
            || !freeRegisters.remove(freeRegisterEnd)) {
          continue;
        }
      }
      freeRegisters.remove(freeRegister);
      return freeRegister;
    }
    return NO_REGISTER;
  }

  private void addActiveTempRegisters(int register, boolean wide) {
    boolean addedRegister = activeTempRegisters.add(register);
    assert addedRegister;
    if (wide) {
      boolean addedHighRegister = activeTempRegisters.add(register + 1);
      assert addedHighRegister;
    }
  }

  private void returnTemporaryRegister(int register, boolean wide) {
    if (returnActiveTempRegister(register, wide)) {
      addFreeRegistersUntilAssigned(register, wide);
    } else if (isExtraTemporaryRegister(register)) {
      returnExtraTemporaryRegister(register, wide);
    }
  }

  private boolean returnActiveTempRegister(int register, boolean wide) {
    boolean removedRegister = activeTempRegisters.remove(register);
    if (wide) {
      if (removedRegister) {
        boolean removedHighRegister = activeTempRegisters.remove(register + 1);
        assert removedHighRegister;
      } else {
        assert !activeTempRegisters.contains(register + 1);
      }
    }
    return removedRegister;
  }

  private void addFreeRegistersUntilAssigned(int register, boolean wide) {
    boolean addedRegister = freeRegistersUntilAssigned.add(register);
    assert addedRegister;
    if (wide) {
      boolean addedHighRegister = freeRegistersUntilAssigned.add(register + 1);
      assert addedHighRegister;
    }
  }

  private void returnExtraTemporaryRegister(int register, boolean wide) {
    assert isExtraTemporaryRegister(register);
    freeRegisters.add(register);
    if (wide) {
      assert isExtraTemporaryRegister(register + 1);
      freeRegisters.add(register + 1);
    }
  }

  private void removeFromFreeRegistersUntilAssigned(
      int register, boolean wide, IntConsumer changedConsumer) {
    if (freeRegistersUntilAssigned.remove(register)) {
      changedConsumer.accept(register);
    }
    if (wide) {
      if (freeRegistersUntilAssigned.remove(register + 1)) {
        changedConsumer.accept(register + 1);
      }
    }
  }

  private boolean isExtraTemporaryRegister(int register) {
    return register >= firstTempRegister;
  }

  private int allocateExtraRegister() {
    return nextTempRegister++;
  }

  private boolean verifyMovesHaveDifferentSources(List<RegisterMove> movesWithSrc) {
    IntSet seen = new IntOpenHashSet();
    for (RegisterMove move : movesWithSrc) {
      assert seen.add(move.src);
    }
    return true;
  }

  private RegisterMove pickMoveToUnblock(TreeSet<RegisterMove> moves) {
    // Pick a non-wide move to unblock if possible.
    Iterable<RegisterMove> eligible =
        Iterables.filter(moves, move -> !move.isDestUsedAsTemporary(this));
    RegisterMove move =
        Iterables.find(eligible, not(RegisterMove::isWide), eligible.iterator().next());
    moves.remove(move);
    if (ObjectUtils.notIdentical(moves, moveSet)) {
      moveSet.remove(move);
    }
    return move;
  }

  private boolean everyDestinationOnlyWrittenOnce() {
    IntSet destinations = new IntArraySet(moveSet.size());
    for (RegisterMove move : moveSet) {
      boolean changed = destinations.add(move.dst);
      assert changed;
    }
    return true;
  }
}
