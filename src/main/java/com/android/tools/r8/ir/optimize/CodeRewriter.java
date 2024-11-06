// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.ir.code.Assume;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.DebugLocalWrite;
import com.android.tools.r8.ir.code.DebugLocalsChange;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Move;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.regalloc.LinearScanRegisterAllocator;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Set;

public class CodeRewriter {

  private final AppView<?> appView;

  public CodeRewriter(AppView<?> appView) {
    this.appView = appView;
  }

  public static void removeAssumeInstructions(AppView<?> appView, IRCode code) {
    // We need to update the types of all values whose definitions depend on a non-null value.
    // This is needed to preserve soundness of the types after the Assume instructions have been
    // removed.
    //
    // As an example, consider a check-cast instruction on the form "z = (T) y". If y used to be
    // defined by a NonNull instruction, then the type analysis could have used this information
    // to mark z as non-null. However, cleanupNonNull() have now replaced y by a nullable value x.
    // Since z is defined as "z = (T) x", and x is nullable, it is no longer sound to have that z
    // is not nullable. This is fixed by rerunning the type analysis for the affected values.
    AffectedValues valuesThatRequireWidening = new AffectedValues();

    InstructionListIterator it = code.instructionListIterator();
    boolean needToCheckTrivialPhis = false;
    while (it.hasNext()) {
      Instruction instruction = it.next();

      // The following deletes Assume instructions and replaces any specialized value by its
      // original value:
      //   y <- Assume(x)
      //   ...
      //   y.foo()
      //
      // becomes:
      //
      //   x.foo()
      if (instruction.isAssume()) {
        Assume assumeInstruction = instruction.asAssume();
        Value src = assumeInstruction.src();
        Value dest = assumeInstruction.outValue();

        valuesThatRequireWidening.addAll(dest.affectedValues());

        // Replace `dest` by `src`.
        needToCheckTrivialPhis |= dest.numberOfPhiUsers() > 0;
        dest.replaceUsers(src);
        it.remove();
      }
    }

    // Assume insertion may introduce phis, e.g.,
    //   y <- Assume(x)
    //   ...
    //   z <- phi(x, y)
    //
    // Therefore, Assume elimination may result in a trivial phi:
    //   z <- phi(x, x)
    if (needToCheckTrivialPhis) {
      code.removeAllDeadAndTrivialPhis(valuesThatRequireWidening);
    }

    valuesThatRequireWidening.widening(appView, code);

    code.removeRedundantBlocks();

    assert Streams.stream(code.instructions()).noneMatch(Instruction::isAssume);
    assert code.isConsistentSSA(appView);
  }

  @SuppressWarnings("ReferenceEquality")
  public static void removeOrReplaceByDebugLocalWrite(
      Instruction currentInstruction, InstructionListIterator it, Value inValue, Value outValue) {
    if (outValue.hasLocalInfo() && outValue.getLocalInfo() != inValue.getLocalInfo()) {
      DebugLocalWrite debugLocalWrite = new DebugLocalWrite(outValue, inValue);
      it.replaceCurrentInstruction(debugLocalWrite);
    } else {
      if (outValue.hasLocalInfo()) {
        assert outValue.getLocalInfo() == inValue.getLocalInfo();
        // Should remove the end-marker before replacing the current instruction.
        currentInstruction.removeDebugValue(outValue.getLocalInfo());
      }
      outValue.replaceUsers(inValue);
      it.removeOrReplaceByDebugLocalRead();
    }
  }

  @SuppressWarnings("ReferenceEquality")
  // TODO(mikaelpeltier) Manage that from and to instruction do not belong to the same block.
  private static boolean hasLocalOrLineChangeBetween(
      Instruction from, Instruction to, DexString localVar) {
    if (from.getBlock() != to.getBlock()) {
      return true;
    }
    if (from.getPosition().isSome()
        && to.getPosition().isSome()
        && !from.getPosition().equals(to.getPosition())) {
      return true;
    }
    Position position = null;
    for (Instruction instruction : from.getBlock().instructionsAfter(from)) {
      if (position == null) {
        if (instruction.getPosition().isSome()) {
          position = instruction.getPosition();
        }
      } else if (instruction.getPosition().isSome()
          && !position.equals(instruction.getPosition())) {
        return true;
      }
      if (instruction == to) {
        return false;
      }
      if (instruction.outValue() != null && instruction.outValue().hasLocalInfo()) {
        if (instruction.outValue().getLocalInfo().name == localVar) {
          return true;
        }
      }
    }
    throw new Unreachable();
  }

  public void simplifyDebugLocals(IRCode code) {
    for (BasicBlock block : code.blocks) {
      InstructionListIterator iterator = block.listIterator(code);
      while (iterator.hasNext()) {
        Instruction prevInstruction = iterator.peekPrevious();
        Instruction instruction = iterator.next();
        if (instruction.isDebugLocalWrite()) {
          assert instruction.inValues().size() == 1;
          Value inValue = instruction.inValues().get(0);
          DebugLocalInfo localInfo = instruction.outValue().getLocalInfo();
          DexString localName = localInfo.name;
          if (!inValue.hasLocalInfo() &&
              inValue.numberOfAllUsers() == 1 &&
              inValue.definition != null &&
              !hasLocalOrLineChangeBetween(inValue.definition, instruction, localName)) {
            inValue.setLocalInfo(localInfo);
            instruction.outValue().replaceUsers(inValue);
            Value overwrittenLocal = instruction.removeDebugValue(localInfo);
            if (overwrittenLocal != null) {
              overwrittenLocal.addDebugLocalEnd(inValue.definition);
            }
            if (prevInstruction != null &&
                (prevInstruction.outValue() == null
                    || !prevInstruction.outValue().hasLocalInfo()
                    || !instruction.getDebugValues().contains(prevInstruction.outValue()))) {
              instruction.moveDebugValues(prevInstruction);
            }
            iterator.removeOrReplaceByDebugLocalRead();
          }
        }
      }
    }
    assert code.isConsistentSSA(appView);
  }

  /**
   * Remove moves that are not actually used by instructions in exiting paths. These moves can arise
   * due to debug local info needing a particular value and the live-interval for it then moves it
   * back into the properly assigned register. If the register is only used for debug purposes, it
   * is safe to just remove the move and update the local information accordingly.
   */
  public static void removeUnneededMovesOnExitingPaths(
      IRCode code, LinearScanRegisterAllocator allocator) {
    if (!allocator.options().debug) {
      return;
    }
    for (BasicBlock block : code.blocks) {
      // Skip non-exit blocks.
      if (!block.getSuccessors().isEmpty()) {
        continue;
      }
      // Skip blocks with no locals at entry.
      Int2ReferenceMap<DebugLocalInfo> localsAtEntry = block.getLocalsAtEntry();
      if (localsAtEntry == null || localsAtEntry.isEmpty()) {
        continue;
      }
      // Find the locals state after spill moves.
      DebugLocalsChange postSpillLocalsChange = null;
      for (Instruction instruction : block.getInstructions()) {
        if (instruction.getNumber() != -1 || postSpillLocalsChange != null) {
          break;
        }
        postSpillLocalsChange = instruction.asDebugLocalsChange();
      }
      // Skip if the locals state did not change.
      if (postSpillLocalsChange == null
          || !postSpillLocalsChange.apply(new Int2ReferenceOpenHashMap<>(localsAtEntry))) {
        continue;
      }
      // Collect the moves that can safely be removed.
      Set<Move> unneededMoves = computeUnneededMoves(block, postSpillLocalsChange, allocator);
      if (unneededMoves.isEmpty()) {
        continue;
      }
      Int2IntMap previousMapping = new Int2IntOpenHashMap();
      Int2IntMap mapping = new Int2IntOpenHashMap();
      InstructionListIterator it = block.listIterator(code);
      while (it.hasNext()) {
        Instruction instruction = it.next();
        if (instruction.isMove()) {
          Move move = instruction.asMove();
          int dst = allocator.getRegisterForValue(move.dest(), move.getNumber());
          if (unneededMoves.contains(move)) {
            int src = allocator.getRegisterForValue(move.src(), move.getNumber());
            int mappedSrc = mapping.getOrDefault(src, src);
            mapping.put(dst, mappedSrc);
            it.removeInstructionIgnoreOutValue();
          } else {
            mapping.remove(dst);
          }
        } else if (instruction.isDebugLocalsChange()) {
          DebugLocalsChange change = instruction.asDebugLocalsChange();
          updateDebugLocalsRegisterMap(previousMapping, change.getEnding());
          updateDebugLocalsRegisterMap(mapping, change.getStarting());
          previousMapping = mapping;
          mapping = new Int2IntOpenHashMap(previousMapping);
        }
      }
    }
  }

  private static Set<Move> computeUnneededMoves(
      BasicBlock block,
      DebugLocalsChange postSpillLocalsChange,
      LinearScanRegisterAllocator allocator) {
    Set<Move> unneededMoves = Sets.newIdentityHashSet();
    IntSet usedRegisters = new IntOpenHashSet();
    IntSet clobberedRegisters = new IntOpenHashSet();
    // Backwards instruction scan collecting the registers used by actual instructions.
    boolean inEntrySpillMoves = false;
    for (Instruction ins = block.getLastInstruction(); ins != null; ins = ins.getPrev()) {
      if (ins == postSpillLocalsChange) {
        inEntrySpillMoves = true;
      }
      // If this is a move in the block-entry spill moves check if it is unneeded.
      if (inEntrySpillMoves && ins.isMove()) {
        Move move = ins.asMove();
        int dst = allocator.getRegisterForValue(move.dest(), move.getNumber());
        int src = allocator.getRegisterForValue(move.src(), move.getNumber());
        if (!usedRegisters.contains(dst) && !clobberedRegisters.contains(src)) {
          unneededMoves.add(move);
          continue;
        }
      }
      if (ins.outValue() != null && ins.outValue().needsRegister()) {
        int register = allocator.getRegisterForValue(ins.outValue(), ins.getNumber());
        // The register is defined anew, so uses before this are on distinct values.
        usedRegisters.remove(register);
        // Mark it clobbered to avoid any uses in locals after this point to become invalid.
        clobberedRegisters.add(register);
      }
      if (!ins.inValues().isEmpty()) {
        for (Value inValue : ins.inValues()) {
          if (inValue.needsRegister()) {
            int register = allocator.getRegisterForValue(inValue, ins.getNumber());
            // Record the register as being used.
            usedRegisters.add(register);
          }
        }
      }
    }
    return unneededMoves;
  }

  private static void updateDebugLocalsRegisterMap(
      Int2IntMap mapping, Int2ReferenceMap<DebugLocalInfo> locals) {
    // If nothing is mapped nothing needs to be changed.
    if (mapping.isEmpty()) {
      return;
    }
    // Locals is final, so we copy and clear it during update.
    Int2ReferenceMap<DebugLocalInfo> copy = new Int2ReferenceOpenHashMap<>(locals);
    locals.clear();
    for (Entry<DebugLocalInfo> entry : copy.int2ReferenceEntrySet()) {
      int oldRegister = entry.getIntKey();
      int newRegister = mapping.getOrDefault(oldRegister, oldRegister);
      locals.put(newRegister, entry.getValue());
    }
  }
}
