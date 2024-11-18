// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.regalloc;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.FixedRegisterValue;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Move;
import com.android.tools.r8.utils.ListUtils;
import com.google.common.base.Equivalence;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.Iterables;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class MoveSorter {

  private static final Equivalence<Instruction> equivalence = new MoveEquivalence();

  private final IRCode code;

  public MoveSorter(IRCode code) {
    this.code = code;
  }

  public void sortMovesForSuffixSharing() {
    for (BasicBlock block : code.blocks(this::hasTwoPredecessorsWithUniqueSuccessor)) {
      // First compute the set of moves that are in the suffix of both predecessor blocks. These are
      // the candidates for suffix sharing.
      Set<Wrapper<Instruction>> suffixSharingCandidates = getSuffixSharingCandidates(block);
      if (suffixSharingCandidates.isEmpty()) {
        continue;
      }
      // Compute a one-to-many map where an edge x -> y means that the instruction x cannot be moved
      // past instruction y.
      Map<Wrapper<Instruction>, Set<Instruction>> blockedBy =
          computeBlockedBy(block, suffixSharingCandidates);
      // Compute which instructions can be shared in the successor.
      Set<Wrapper<Instruction>> sharedSuffix =
          blockedBy.isEmpty()
              ? suffixSharingCandidates
              : computeSharedSuffix(suffixSharingCandidates, blockedBy);
      if (!sharedSuffix.isEmpty()) {
        for (BasicBlock predecessor : block.getPredecessors()) {
          sortSuffix(predecessor, sharedSuffix);
        }
      }
    }
  }

  Set<Wrapper<Instruction>> getSuffixSharingCandidates(BasicBlock block) {
    Set<Wrapper<Instruction>> firstSuffix = new HashSet<>();
    for (Instruction instruction = block.getPredecessor(0).exit().getPrev();
        instruction != null && isMove(instruction);
        instruction = instruction.getPrev()) {
      firstSuffix.add(equivalence.wrap(instruction));
    }
    if (firstSuffix.isEmpty()) {
      return firstSuffix;
    }
    Set<Wrapper<Instruction>> suffixSharingCandidates = new HashSet<>();
    for (Instruction instruction = block.getPredecessor(1).exit().getPrev();
        instruction != null && isMove(instruction);
        instruction = instruction.getPrev()) {
      Wrapper<Instruction> wrapper = equivalence.wrap(instruction);
      if (firstSuffix.contains(wrapper)) {
        suffixSharingCandidates.add(wrapper);
      }
    }
    return suffixSharingCandidates;
  }

  Map<Wrapper<Instruction>, Set<Instruction>> computeBlockedBy(
      BasicBlock block, Set<Wrapper<Instruction>> suffixSharingCandidates) {
    Map<Wrapper<Instruction>, Set<Instruction>> blockedBy = new HashMap<>();
    for (BasicBlock predecessor : block.getPredecessors()) {
      addBlockedBy(predecessor, suffixSharingCandidates, blockedBy);
    }
    return blockedBy;
  }

  void addBlockedBy(
      BasicBlock block,
      Set<Wrapper<Instruction>> suffixSharingCandidates,
      Map<Wrapper<Instruction>, Set<Instruction>> blockedBy) {
    // Find the first move.
    Instruction instruction = block.exit().getPrev();
    assert instruction != null;
    assert isMove(instruction);
    while (instruction.hasPrev() && isMove(instruction.getPrev())) {
      instruction = instruction.getPrev();
    }

    List<Instruction> seen = ListUtils.newArrayList(instruction);
    for (instruction = instruction.getNext();
        !instruction.isExit();
        instruction = instruction.getNext()) {
      // Record if the current instruction is blocking any of the previously seen instructions.
      assert isMove(instruction);
      for (Instruction previous : seen) {
        if (isBlockedBy(previous, instruction)) {
          blockedBy
              .computeIfAbsent(equivalence.wrap(previous), ignoreKey(HashSet::new))
              .add(instruction);
        }
      }
      // Only add the current instruction to the seen set if it is a candidate for suffix sharing.
      // Otherwise, we can't move it to the successor so we don't need worry about whether it is
      // blocked.
      if (suffixSharingCandidates.contains(equivalence.wrap(instruction))) {
        seen.add(instruction);
      }
    }
  }

  Set<Wrapper<Instruction>> computeSharedSuffix(
      Set<Wrapper<Instruction>> suffixSharingCandidates,
      Map<Wrapper<Instruction>, Set<Instruction>> blockedBy) {
    Set<Wrapper<Instruction>> sharedSuffix = new HashSet<>();
    while (true) {
      Wrapper<Instruction> unblockedMove =
          removeUnblockedMove(suffixSharingCandidates, blockedBy, sharedSuffix);
      if (unblockedMove == null) {
        break;
      }
      sharedSuffix.add(unblockedMove);
    }
    return sharedSuffix;
  }

  Wrapper<Instruction> removeUnblockedMove(
      Set<Wrapper<Instruction>> suffixSharingCandidates,
      Map<Wrapper<Instruction>, Set<Instruction>> blockedBy,
      Set<Wrapper<Instruction>> sharedSuffix) {
    Iterator<Wrapper<Instruction>> iterator = suffixSharingCandidates.iterator();
    while (iterator.hasNext()) {
      Wrapper<Instruction> candidate = iterator.next();
      Set<Instruction> blockingSet = blockedBy.getOrDefault(candidate, Collections.emptySet());
      // Disregard blocking moves that have already been moved to the successor.
      blockingSet.removeIf(blockingMove -> sharedSuffix.contains(equivalence.wrap(blockingMove)));
      if (blockingSet.isEmpty()) {
        iterator.remove();
        return candidate;
      }
    }
    return null;
  }

  void sortSuffix(BasicBlock predecessor, Set<Wrapper<Instruction>> sharedSuffix) {
    InstructionListIterator iterator =
        predecessor.listIterator(predecessor.getInstructions().size() - 1);
    Deque<Instruction> removedInstructions = new ArrayDeque<>();
    while (iterator.hasPrevious()) {
      Instruction instruction = iterator.previous();
      if (isMove(instruction)) {
        if (sharedSuffix.contains(equivalence.wrap(instruction))) {
          removedInstructions.addFirst(instruction);
          iterator.removeInstructionIgnoreOutValue();
        }
      } else {
        break;
      }
    }
    assert removedInstructions.size() == sharedSuffix.size();
    predecessor.listIterator(predecessor.getInstructions().size() - 1).addAll(removedInstructions);
  }

  private boolean hasTwoPredecessorsWithUniqueSuccessor(BasicBlock block) {
    return block.getPredecessors().size() == 2
        && Iterables.all(
            block.getPredecessors(),
            predecessor -> predecessor.hasUniqueSuccessor() && predecessor.exit().isGoto());
  }

  private boolean isBlockedBy(Instruction instruction, Instruction laterInstruction) {
    // Check if either of the two instructions write the operand of the other instruction.
    if (instruction.isMove()) {
      FixedRegisterValue inValue = instruction.getFirstOperand().asFixedRegisterValue();
      FixedRegisterValue outValue = instruction.outValue().asFixedRegisterValue();
      FixedRegisterValue laterOutValue = laterInstruction.outValue().asFixedRegisterValue();
      if (laterOutValue.usesRegister(inValue) || laterOutValue.usesRegister(outValue)) {
        return true;
      }
    }
    if (laterInstruction.isMove()) {
      FixedRegisterValue outValue = instruction.outValue().asFixedRegisterValue();
      FixedRegisterValue laterInValue = laterInstruction.getFirstOperand().asFixedRegisterValue();
      FixedRegisterValue laterOutValue = laterInstruction.outValue().asFixedRegisterValue();
      if (outValue.usesRegister(laterInValue) || outValue.usesRegister(laterOutValue)) {
        return true;
      }
    }
    return false;
  }

  private boolean isMove(Instruction instruction) {
    if (instruction.isConstNumber()) {
      return instruction.outValue().isFixedRegisterValue();
    }
    if (instruction.isMove() && !instruction.isDebugLocalWrite()) {
      if (instruction.getFirstOperand().isFixedRegisterValue()) {
        assert instruction.outValue().isFixedRegisterValue();
        return true;
      }
      // This Move instruction has been inserted for dealing with invoke/range. It is not inserted
      // by the move scheduler.
    }
    return false;
  }

  private static class MoveEquivalence extends Equivalence<Instruction> {

    @Override
    protected boolean doEquivalent(Instruction a, Instruction b) {
      if (a.isConstNumber()) {
        if (!b.isConstNumber()) {
          return false;
        }
        ConstNumber constNumber = a.asConstNumber();
        ConstNumber other = b.asConstNumber();
        return constNumber.getOutType().equals(other.getOutType())
            && constNumber.getRawValue() == other.getRawValue()
            && constNumber.outValue().asFixedRegisterValue().getRegister()
                == other.outValue().asFixedRegisterValue().getRegister();
      } else {
        assert a.isMove();
        if (!b.isMove()) {
          return false;
        }
        Move move = a.asMove();
        Move other = b.asMove();
        return move.src()
                .asFixedRegisterValue()
                .outType()
                .equals(other.src().asFixedRegisterValue().outType())
            && move.src().asFixedRegisterValue().getRegister()
                == other.src().asFixedRegisterValue().getRegister()
            && move.outValue().asFixedRegisterValue().getRegister()
                == other.outValue().asFixedRegisterValue().getRegister();
      }
    }

    @Override
    protected int doHash(Instruction instruction) {
      if (instruction.isConstNumber()) {
        ConstNumber constNumber = instruction.asConstNumber();
        return Objects.hash(
            constNumber.getClass(),
            constNumber.outValue().asFixedRegisterValue().getRegister(),
            constNumber.getRawValue());
      } else {
        assert instruction.isMove();
        Move move = instruction.asMove();
        return Objects.hash(
            move.getClass(),
            move.outValue().asFixedRegisterValue().getRegister(),
            move.src().asFixedRegisterValue().getRegister());
      }
    }
  }
}
