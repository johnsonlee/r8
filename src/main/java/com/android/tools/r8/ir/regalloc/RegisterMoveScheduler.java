// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.regalloc;

import static com.android.tools.r8.ir.regalloc.LiveIntervals.NO_REGISTER;

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
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class RegisterMoveScheduler {
  // The set of moves to schedule.
  private final Set<RegisterMove> moveSet = new TreeSet<>();
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
  // Free registers.
  private final IntSortedSet freeRegisters = new IntRBTreeSet();

  public RegisterMoveScheduler(
      InstructionListIterator insertAt, int firstTempRegister, Position position) {
    this.insertAt = insertAt;
    this.firstTempRegister = firstTempRegister;
    this.nextTempRegister = firstTempRegister;
    this.position = position;
    this.valueMap.defaultReturnValue(NO_REGISTER);
  }

  public RegisterMoveScheduler(InstructionListIterator insertAt, int firstTempRegister) {
    this(insertAt, firstTempRegister, Position.none());
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

    // Worklist of moves that are ready to be inserted.
    Deque<RegisterMove> worklist = new ArrayDeque<>();

    // Initialize worklist with the moves that do not interfere with other moves.
    Iterator<RegisterMove> iterator = moveSet.iterator();
    while (iterator.hasNext()) {
      RegisterMove move = iterator.next();
      if (!move.isBlocked(moveSet, valueMap)) {
        worklist.addLast(move);
        iterator.remove();
      }
    }

    // Process the worklist generating moves. If the worklist becomes empty while the move set
    // still contains elements we need to use a temporary to break cycles.
    while (!worklist.isEmpty() || !moveSet.isEmpty()) {
      while (!worklist.isEmpty()) {
        RegisterMove move = worklist.removeFirst();
        assert !move.isBlocked(moveSet, valueMap);
        // Insert the move.
        int generatedDest = createMove(move);
        // Update the value map with the information that dest can be used instead of
        // src starting now.
        if (move.src != NO_REGISTER) {
          valueMap.put(move.src, generatedDest);
        }
        // Iterate and find the moves that were blocked because they need to write to
        // one of the move src. That is now valid because the move src is preserved in dest.
        iterator = moveSet.iterator();
        while (iterator.hasNext()) {
          RegisterMove other = iterator.next();
          if (!other.isBlocked(moveSet, valueMap)) {
            worklist.addLast(other);
            iterator.remove();
          }
        }
      }
      if (!moveSet.isEmpty()) {
        // The remaining moves are conflicting. Chose a move and unblock it by generating moves to
        // temporary registers for its destination value(s).
        RegisterMove move = pickMoveToUnblock();
        createMoveDestToTemp(move);
        // TODO(b/375147902): After emitting the newly unblocked move, try to prioritize the moves
        //  that blocked it, so that we free up the temp register, rather than getting overlapping
        //  temporary registers.
        worklist.addLast(move);
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
      } else if (move.type.isWidePrimitive() && (moveSrc + 1) == src) {
        result.add(move);
      } else if (type.isWidePrimitive() && (moveSrc - 1) == src) {
        result.add(move);
      }
    }
    return result;
  }

  private int createMove(RegisterMove move) {
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
    return move.dst;
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
    for (int freeRegister : freeRegisters) {
      if (wide && !freeRegisters.remove(freeRegister + 1)) {
        continue;
      }
      freeRegisters.remove(freeRegister);
      return freeRegister;
    }
    // We don't have a free register.
    int register = allocateExtraRegister();
    if (!wide) {
      return register;
    }
    if (freeRegisters.remove(register - 1)) {
      return register - 1;
    }
    allocateExtraRegister();
    return register;
  }

  private void returnTemporaryRegister(int register, boolean wide) {
    // TODO(b/375147902): If we seed the move scheduler with a set of free registers, then this
    //  should also return non-temporary registers that are below firstTempRegister.
    if (isTemporaryRegister(register)) {
      freeRegisters.add(register);
      if (wide) {
        assert isTemporaryRegister(register + 1);
        freeRegisters.add(register + 1);
      }
    } else if (wide && isTemporaryRegister(register + 1)) {
      freeRegisters.add(register + 1);
    }
  }

  private boolean isTemporaryRegister(int register) {
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

  private RegisterMove pickMoveToUnblock() {
    Iterator<RegisterMove> iterator = moveSet.iterator();
    RegisterMove move = null;
    // Pick a non-wide move to unblock if possible.
    while (iterator.hasNext()) {
      move = iterator.next();
      if (!move.type.isWidePrimitive()) {
        break;
      }
    }
    iterator.remove();
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
