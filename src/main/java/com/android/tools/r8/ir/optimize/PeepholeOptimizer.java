// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.ir.conversion.passes.TrivialGotosCollapser.unlinkTrivialGotoBlock;
import static com.android.tools.r8.ir.regalloc.LiveIntervals.NO_REGISTER;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.DebugLocalsChange;
import com.android.tools.r8.ir.code.Goto;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionList;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.regalloc.LinearScanRegisterAllocator;
import com.android.tools.r8.ir.regalloc.LiveIntervals;
import com.android.tools.r8.ir.regalloc.RegisterAllocator;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class PeepholeOptimizer {
  /**
   * Perform optimizations of the code with register assignments provided by the register allocator.
   */
  public static void optimize(
      AppView<?> appView, IRCode code, LinearScanRegisterAllocator allocator) {
    removeIdenticalPredecessorBlocks(code, allocator);
    removeRedundantInstructions(code, allocator);
    shareIdenticalBlockPrefix(code, allocator);
    shareIdenticalBlockSuffix(code, allocator, 0);
    assert code.isConsistentGraph(appView);
  }

  /** Identify common prefixes in successor blocks and share them. */
  private static void shareIdenticalBlockPrefix(IRCode code, RegisterAllocator allocator) {
    InstructionEquivalence equivalence = new InstructionEquivalence(allocator, code);
    Set<BasicBlock> blocksToBeRemoved = Sets.newIdentityHashSet();
    for (BasicBlock block : code.blocks) {
      shareIdenticalBlockPrefixFromNormalSuccessors(
          block, allocator, blocksToBeRemoved, equivalence);
    }
    code.blocks.removeAll(blocksToBeRemoved);
  }

  private static void shareIdenticalBlockPrefixFromNormalSuccessors(
      BasicBlock block,
      RegisterAllocator allocator,
      Set<BasicBlock> blocksToBeRemoved,
      InstructionEquivalence equivalence) {
    if (blocksToBeRemoved.contains(block)) {
      // This block has already been removed entirely.
      return;
    }

    if (!mayShareIdenticalBlockPrefix(block)) {
      // Not eligible.
      return;
    }

    // Share instructions from the normal successors one-by-one.
    List<BasicBlock> normalSuccessors = block.getNormalSuccessors();
    while (true) {
      BasicBlock firstNormalSuccessor = normalSuccessors.get(0);

      // Check if all instructions were already removed from the normal successors.
      for (BasicBlock normalSuccessor : normalSuccessors) {
        if (normalSuccessor.isEmpty()) {
          assert blocksToBeRemoved.containsAll(normalSuccessors);
          return;
        }
      }

      // If the first instruction in all successors is not the same, we cannot merge them into the
      // predecessor.
      Instruction instruction = firstNormalSuccessor.entry();
      for (int i = 1; i < normalSuccessors.size(); i++) {
        BasicBlock otherNormalSuccessor = normalSuccessors.get(i);
        Instruction otherInstruction = otherNormalSuccessor.entry();
        if (!equivalence.equivalent(instruction, otherInstruction)) {
          return;
        }
      }

      if (instruction.instructionTypeCanThrow()) {
        // Each block with one or more catch handlers may have at most one throwing instruction.
        if (block.hasCatchHandlers()) {
          return;
        }

        // If the instruction can throw and one of the normal successor blocks has a catch handler,
        // then we cannot merge the instruction into the predecessor, since this would change the
        // exceptional control flow.
        for (BasicBlock normalSuccessor : normalSuccessors) {
          if (normalSuccessor.hasCatchHandlers()) {
            return;
          }
        }
      }

      // Check for commutativity (semantics). If the instruction writes to a register, then we
      // need to make sure that the instruction commutes with the last instruction in the
      // predecessor. Consider the following example.
      //
      //                    <Block A>
      //   if-eqz r0 then goto Block B else goto Block C
      //           /                         \
      //      <Block B>                  <Block C>
      //     const r0, 1                const r0, 1
      //       ...                        ...
      //
      // In the example, it is not possible to change the order of "if-eqz r0" and
      // "const r0, 1" without changing the semantics.
      if (instruction.outValue() != null && instruction.outValue().needsRegister()) {
        int destinationRegister =
            allocator.getRegisterForValue(instruction.outValue(), instruction.getNumber());
        boolean commutative =
            block.exit().inValues().stream()
                .allMatch(
                    inValue -> {
                      int operandRegister =
                          allocator.getRegisterForValue(inValue, block.exit().getNumber());
                      for (int i = 0; i < instruction.outValue().requiredRegisters(); i++) {
                        for (int j = 0; j < inValue.requiredRegisters(); j++) {
                          if (destinationRegister + i == operandRegister + j) {
                            // Overlap detected, the two instructions do not commute.
                            return false;
                          }
                        }
                      }
                      return true;
                    });
        if (!commutative) {
          return;
        }
      }

      // Check for commutativity (debug info).
      if (!instruction.getPosition().equals(block.exit().getPosition())
          && !(block.exit().getPosition().isNone() && !block.exit().getDebugValues().isEmpty())) {
        return;
      }

      // Remove the instruction from the normal successors.
      for (BasicBlock normalSuccessor : normalSuccessors) {
        normalSuccessor.entry().removeIgnoreValues();
      }

      // Move the instruction into the predecessor.
      if (instruction.isJumpInstruction()) {
        // Replace jump instruction in predecessor with the jump instruction from the normal
        // successors.
        block.getLastInstruction().removeIgnoreValues();
        block.getInstructions().addLast(instruction);

        // Take a copy of the old normal successors before performing a destructive update.
        List<BasicBlock> oldNormalSuccessors = new ArrayList<>(normalSuccessors);

        // Update successors of predecessor block.
        block.detachAllSuccessors();
        for (BasicBlock newNormalSuccessor : firstNormalSuccessor.getNormalSuccessors()) {
          block.link(newNormalSuccessor);
        }

        // Detach the previous normal successors from the rest of the graph.
        for (BasicBlock oldNormalSuccessor : oldNormalSuccessors) {
          oldNormalSuccessor.detachAllSuccessors();
        }

        // Record that the previous normal successors should be removed entirely.
        blocksToBeRemoved.addAll(oldNormalSuccessors);

        if (!mayShareIdenticalBlockPrefix(block)) {
          return;
        }
      } else {
        // Insert instruction before the jump instruction in the predecessor.
        block.getInstructions().addBefore(instruction, block.getLastInstruction());

        // Update locals-at-entry if needed.
        if (instruction.isDebugLocalsChange()) {
          DebugLocalsChange localsChange = instruction.asDebugLocalsChange();
          for (BasicBlock normalSuccessor : normalSuccessors) {
            localsChange.apply(normalSuccessor.getLocalsAtEntry());
          }
        }
      }
    }
  }

  private static boolean mayShareIdenticalBlockPrefix(BasicBlock block) {
    List<BasicBlock> normalSuccessors = block.getNormalSuccessors();
    if (normalSuccessors.size() <= 1) {
      // Nothing to share.
      return false;
    }

    // Ensure that the current block is on all paths to the successor blocks.
    for (BasicBlock normalSuccessor : normalSuccessors) {
      if (normalSuccessor.getPredecessors().size() != 1) {
        return false;
      }
    }

    // Only try to share instructions if the normal successors have the same locals state on entry.
    BasicBlock firstNormalSuccessor = normalSuccessors.get(0);
    for (int i = 1; i < normalSuccessors.size(); i++) {
      BasicBlock otherNormalSuccessor = normalSuccessors.get(i);
      if (!Objects.equals(
          firstNormalSuccessor.getLocalsAtEntry(), otherNormalSuccessor.getLocalsAtEntry())) {
        return false;
      }
    }
    return true;
  }

  /** Identify common suffixes in predecessor blocks and share them. */
  public static void shareIdenticalBlockSuffix(
      IRCode code, RegisterAllocator allocator, int overhead) {
    Collection<BasicBlock> blocks = code.blocks;
    List<BasicBlock> normalExits = code.computeNormalExitBlocks();
    Set<BasicBlock> syntheticNormalExits = Sets.newIdentityHashSet();
    if (normalExits.size() > 1) {
      if (code.context().getReturnType().isVoidType()
          || code.getConversionOptions().isGeneratingClassFiles()) {
        BasicBlock syntheticNormalExit = new BasicBlock(code.metadata());
        syntheticNormalExit.getMutablePredecessors().addAll(normalExits);
        syntheticNormalExits.add(syntheticNormalExit);
      } else {
        Int2ReferenceMap<List<BasicBlock>> normalExitPartitioning =
            new Int2ReferenceOpenHashMap<>();
        for (BasicBlock block : normalExits) {
          int returnRegister =
              block
                  .exit()
                  .asReturn()
                  .returnValue()
                  .getLiveIntervals()
                  .getSplitCovering(block.exit().getNumber())
                  .getRegister();
          assert returnRegister != NO_REGISTER;
          List<BasicBlock> blocksWithReturnRegister;
          if (normalExitPartitioning.containsKey(returnRegister)) {
            blocksWithReturnRegister = normalExitPartitioning.get(returnRegister);
          } else {
            blocksWithReturnRegister = new ArrayList<>();
            normalExitPartitioning.put(returnRegister, blocksWithReturnRegister);
          }
          blocksWithReturnRegister.add(block);
        }
        for (List<BasicBlock> blocksWithSameReturnRegister : normalExitPartitioning.values()) {
          BasicBlock syntheticNormalExit = new BasicBlock(code.metadata());
          syntheticNormalExit.getMutablePredecessors().addAll(blocksWithSameReturnRegister);
          syntheticNormalExits.add(syntheticNormalExit);
        }
      }
      blocks = new ArrayList<>(code.getBlocks().size() + syntheticNormalExits.size());
      blocks.addAll(code.getBlocks());
      blocks.addAll(syntheticNormalExits);
    }
    do {
      Map<BasicBlock, BasicBlock> newBlocks = new IdentityHashMap<>();
      for (BasicBlock block : blocks) {
        InstructionEquivalence equivalence = new InstructionEquivalence(allocator, code);
        // Group interesting predecessor blocks by their last instruction.
        Map<Wrapper<Instruction>, List<BasicBlock>> lastInstructionToBlocks = new HashMap<>();
        for (BasicBlock pred : block.getPredecessors()) {
          // Only deal with predecessors with one successor. This way we can move throwing
          // instructions as well since there are no handlers (or the handler is the same as the
          // normal control-flow block). Alternatively, we could move only non-throwing instructions
          // and allow both a goto edge and exception edges when the target does not start with a
          // MoveException instruction. However, that would require us to require rewriting of
          // catch handlers as well.
          if (pred.exit().isGoto()
              && pred.getSuccessors().size() == 1
              && pred.getInstructions().size() > 1) {
            Instruction lastInstruction = pred.getLastInstruction().getPrev();
            List<BasicBlock> value = lastInstructionToBlocks.computeIfAbsent(
                equivalence.wrap(lastInstruction), (k) -> new ArrayList<>());
            value.add(pred);
          } else if (pred.exit().isReturn()
              && pred.getSuccessors().isEmpty()
              && pred.getInstructions().size() > 2) {
            Instruction lastInstruction = pred.exit();
            List<BasicBlock> value =
                lastInstructionToBlocks.computeIfAbsent(
                    equivalence.wrap(lastInstruction), (k) -> new ArrayList<>());
            value.add(pred);
          }
        }
        // For each group of predecessors of size 2 or more, find the largest common suffix and
        // move that to a separate block.
        for (List<BasicBlock> predsWithSameLastInstruction : lastInstructionToBlocks.values()) {
          if (predsWithSameLastInstruction.size() < 2) {
            continue;
          }
          BasicBlock firstPred = predsWithSameLastInstruction.get(0);
          int commonSuffixSize = firstPred.getInstructions().size();
          for (int i = 1; i < predsWithSameLastInstruction.size(); i++) {
            BasicBlock pred = predsWithSameLastInstruction.get(i);
            assert pred.exit().isGoto() || pred.exit().isReturn();
            commonSuffixSize =
                Math.min(commonSuffixSize, sharedSuffixSize(firstPred, pred, allocator, code));
          }

          int sizeDelta = overhead - (predsWithSameLastInstruction.size() - 1) * commonSuffixSize;

          // Don't share a suffix that is just a single goto or return instruction.
          if (commonSuffixSize <= 1 || sizeDelta >= 0) {
            continue;
          }
          BasicBlock newBlock =
              createAndInsertBlockForSuffix(
                  code,
                  commonSuffixSize,
                  predsWithSameLastInstruction,
                  syntheticNormalExits.contains(block) ? null : block,
                  allocator);
          newBlocks.put(predsWithSameLastInstruction.get(0), newBlock);
        }
      }
      ListIterator<BasicBlock> blockIterator = code.listIterator();
      while (blockIterator.hasNext()) {
        BasicBlock block = blockIterator.next();
        if (newBlocks.containsKey(block)) {
          blockIterator.add(newBlocks.get(block));
        }
      }
      // Go through all the newly introduced blocks to find more common suffixes to share.
      blocks = newBlocks.values();
    } while (!blocks.isEmpty());
  }

  private static BasicBlock createAndInsertBlockForSuffix(
      IRCode code,
      int suffixSize,
      List<BasicBlock> preds,
      BasicBlock successorBlock,
      RegisterAllocator allocator) {
    BasicBlock first = preds.get(0);
    assert (successorBlock != null && first.exit().isGoto())
        || (successorBlock == null && first.exit().isReturn());
    BasicBlock newBlock = new BasicBlock(code.metadata());
    newBlock.setNumber(code.getNextBlockNumber());
    Int2ReferenceMap<DebugLocalInfo> newBlockEntryLocals = null;
    if (first.getLocalsAtEntry() != null) {
      newBlockEntryLocals = new Int2ReferenceOpenHashMap<>(first.getLocalsAtEntry());
      int prefixSize = first.getInstructions().size() - suffixSize;
      Instruction instruction = first.entry();
      for (int i = 0; i < prefixSize; i++) {
        if (instruction.isDebugLocalsChange()) {
          instruction.asDebugLocalsChange().apply(newBlockEntryLocals);
        }
        instruction = instruction.getNext();
      }
    }

    allocator.addNewBlockToShareIdenticalSuffix(newBlock, suffixSize, preds);

    boolean movedThrowingInstruction = false;
    Instruction instruction = first.getLastInstruction();
    for (int i = 0; i < suffixSize; i++) {
      movedThrowingInstruction = movedThrowingInstruction || instruction.instructionTypeCanThrow();
      instruction = instruction.getPrev();
    }
    newBlock
        .getInstructions()
        .severFrom(instruction == null ? first.entry() : instruction.getNext());
    if (movedThrowingInstruction && first.hasCatchHandlers()) {
      newBlock.transferCatchHandlers(first);
    }

    for (BasicBlock pred : preds) {
      Position lastPosition;
      InstructionList instructions = pred.getInstructions();
      if (pred == first) {
        // Already removed from first via severFrom().
        lastPosition = newBlock.getPosition();
      } else {
        lastPosition = pred.getPosition();
        for (int i = 0; i < suffixSize; i++) {
          instructions.removeIgnoreValues(instructions.getLast());
        }
      }
      for (Instruction ins = instructions.getLastOrNull(); ins != null; ins = ins.getPrev()) {
        if (ins.getPosition().isSome()) {
          lastPosition = ins.getPosition();
          break;
        }
      }
      Goto jump = new Goto();
      jump.setPosition(lastPosition);
      instructions.addLast(jump);
      newBlock.getMutablePredecessors().add(pred);
      if (successorBlock != null) {
        pred.replaceSuccessor(successorBlock, newBlock);
        successorBlock.getMutablePredecessors().remove(pred);
      } else {
        pred.getMutableSuccessors().add(newBlock);
      }
      if (movedThrowingInstruction) {
        pred.clearCatchHandlers();
      }
    }
    newBlock.close(null);
    if (newBlockEntryLocals != null) {
      newBlock.setLocalsAtEntry(newBlockEntryLocals);
    }
    if (successorBlock != null) {
      newBlock.link(successorBlock);
    }
    return newBlock;
  }

  private static Int2ReferenceMap<DebugLocalInfo> localsAtBlockExit(BasicBlock block) {
    if (block.getLocalsAtEntry() == null) {
      return null;
    }
    Int2ReferenceMap<DebugLocalInfo> locals =
        new Int2ReferenceOpenHashMap<>(block.getLocalsAtEntry());
    for (Instruction instruction : block.getInstructions()) {
      if (instruction.isDebugLocalsChange()) {
        instruction.asDebugLocalsChange().apply(locals);
      }
    }
    return locals;
  }

  private static int sharedSuffixSize(
      BasicBlock block0, BasicBlock block1, RegisterAllocator allocator, IRCode code) {
    assert block0.exit().isGoto() || block0.exit().isReturn();
    // If the blocks do not agree on locals at exit then they don't have any shared suffix.
    if (!Objects.equals(localsAtBlockExit(block0), localsAtBlockExit(block1))) {
      return 0;
    }
    Instruction i0 = block0.getLastInstruction();
    Instruction i1 = block1.getLastInstruction();
    int suffixSize = 0;
    while (i0 != null && i1 != null) {
      if (!i0.identicalAfterRegisterAllocation(i1, allocator, code.getConversionOptions())) {
        return suffixSize;
      }
      i0 = i0.getPrev();
      i1 = i1.getPrev();
      suffixSize++;
    }
    return suffixSize;
  }

  /**
   * If two predecessors have the same code and successors. Replace one of them with an empty block
   * with a goto to the other.
   */
  public static void removeIdenticalPredecessorBlocks(IRCode code, RegisterAllocator allocator) {
    Set<BasicBlock> blocksToRemove = Sets.newIdentityHashSet();
    BasicBlockInstructionsEquivalence equivalence =
        new BasicBlockInstructionsEquivalence(code, allocator);
    // Locate one block at a time that has identical predecessors. Rewrite those predecessors and
    // then start over. Restarting when one blocks predecessors have been rewritten simplifies
    // the rewriting and reduces the size of the data structures.
    boolean changed;
    do {
      changed = false;
      for (BasicBlock block : code.blocks) {
        Map<Wrapper<BasicBlock>, Integer> blockToIndex = new HashMap<>();
        for (int predIndex = 0; predIndex < block.getPredecessors().size(); predIndex++) {
          BasicBlock pred = block.getPredecessors().get(predIndex);
          if (pred.getInstructions().size() == 1) {
            continue;
          }
          Wrapper<BasicBlock> wrapper = equivalence.wrap(pred);
          if (blockToIndex.containsKey(wrapper)) {
            changed = true;
            int otherPredIndex = blockToIndex.get(wrapper);
            BasicBlock otherPred = block.getPredecessors().get(otherPredIndex);
            assert !allocator.options().debug
                || Objects.equals(pred.getPosition(), otherPred.getPosition());
            allocator.mergeBlocks(otherPred, pred);
            pred.clearCatchHandlers();
            pred.getInstructions().clear();
            equivalence.clearComputedHash(pred);
            for (BasicBlock succ : pred.getSuccessors()) {
              succ.removePredecessor(pred, null);
            }
            pred.getMutableSuccessors().clear();
            pred.getMutableSuccessors().add(otherPred);
            assert !otherPred.getPredecessors().contains(pred);
            otherPred.getMutablePredecessors().add(pred);
            Goto exit = new Goto();
            exit.setPosition(otherPred.getPosition());
            pred.getInstructions().addLast(exit);

            // If `otherPred` is a catch handler with a `move-exception`, then we cannot
            // `goto otherPred`. In this case we eliminate the goto and remove the `pred` block.
            if (otherPred.entry().isMoveException()) {
              unlinkTrivialGotoBlock(pred, otherPred);
              blocksToRemove.add(pred);
            }
          } else {
            blockToIndex.put(wrapper, predIndex);
          }
        }
      }
    } while (changed);
    code.removeBlocks(blocksToRemove);
  }

  /**
   * Remove redundant instructions from the code.
   *
   * <p>Currently removes move instructions with the same src and target register and const
   * instructions where the constant is known to be in the register already.
   *
   * @param code the code from which to remove redundant instruction
   * @param allocator the register allocator providing registers for values
   */
  private static void removeRedundantInstructions(
      IRCode code, LinearScanRegisterAllocator allocator) {
    for (BasicBlock block : code.blocks) {
      // Mapping from register number to const number instructions for this basic block.
      // Used to remove redundant const instructions that reloads the same constant into
      // the same register.
      Map<Integer, ConstNumber> registerToNumber = new HashMap<>();
      MoveEliminator moveEliminator = new MoveEliminator(allocator);
      InstructionListIterator iterator = block.listIterator();
      while (iterator.hasNext()) {
        Instruction current = iterator.next();
        if (moveEliminator.shouldBeEliminated(current)) {
          iterator.removeInstructionIgnoreOutValue();
        } else if (current.outValue() != null && current.outValue().needsRegister()) {
          Value outValue = current.outValue();
          int instructionNumber = current.getNumber();
          if (outValue.isConstant() && current.isConstNumber()) {
            if (constantSpilledAtDefinition(current.asConstNumber())) {
              // Remove constant instructions that are spilled at their definition and are
              // therefore unused.
              iterator.removeInstructionIgnoreOutValue();
              continue;
            }
            int outRegister = allocator.getRegisterForValue(outValue, instructionNumber);
            ConstNumber numberInRegister = registerToNumber.get(outRegister);
            if (numberInRegister != null
                && numberInRegister.identicalNonValueNonPositionParts(current)) {
              // This instruction is not needed, the same constant is already in this register.
              // We don't consider the positions of the two (non-throwing) instructions.
              iterator.removeInstructionIgnoreOutValue();
            } else {
              // Insert the current constant in the mapping. Make sure to clobber the second
              // register if wide and register-1 if that defines a wide value.
              registerToNumber.put(outRegister, current.asConstNumber());
              if (current.outType().isWide()) {
                registerToNumber.remove(outRegister + 1);
              }
              removeWideConstantCovering(registerToNumber, outRegister);
            }
          } else {
            // This instruction writes registers with a non-constant value. Remove the registers
            // from the mapping.
            int outRegister = allocator.getRegisterForValue(outValue, instructionNumber);
            for (int i = 0; i < outValue.requiredRegisters(); i++) {
              registerToNumber.remove(outRegister + i);
            }
            // Check if the first register written is the second part of a wide value. If so
            // the wide value is no longer active.
            removeWideConstantCovering(registerToNumber, outRegister);
          }
        }
      }
    }
  }

  private static void removeWideConstantCovering(
      Map<Integer, ConstNumber> registerToNumber, int register) {
    ConstNumber number = registerToNumber.get(register - 1);
    if (number != null && number.outType().isWide()) {
      registerToNumber.remove(register - 1);
    }
  }

  private static boolean constantSpilledAtDefinition(ConstNumber constNumber) {
    if (constNumber.outValue().isFixedRegisterValue()) {
      return false;
    }
    LiveIntervals definitionIntervals =
        constNumber.outValue().getLiveIntervals().getSplitCovering(constNumber.getNumber());
    return definitionIntervals.isSpilledAndRematerializable();
  }
}
