// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion.passes;

import static com.android.tools.r8.utils.ConsumerUtils.emptyConsumer;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Return;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.ir.optimize.AffectedValues;
import com.android.tools.r8.utils.WorkList;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;

public class SplitReturnRewriter extends CodeRewriterPass<AppInfo> {

  public SplitReturnRewriter(AppView<?> appView) {
    super(appView);
  }

  @Override
  protected String getRewriterId() {
    return "SplitReturnRewriter";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code, MethodProcessor methodProcessor) {
    // Disable in tests that need dead switches to be left behind.
    assert options.isRelease();
    return appView.options().getTestingOptions().enableDeadSwitchCaseElimination;
  }

  @Override
  protected CodeRewriterResult rewriteCode(IRCode code) {
    int color = colorExceptionHandlers(code);
    AffectedValues affectedValues = new AffectedValues();
    boolean changed = false;
    boolean hasUnreachableBlocks = false;
    Set<BasicBlock> blocksToRemove = Sets.newIdentityHashSet();
    Deque<BasicBlock> worklist = new ArrayDeque<>(code.computeNormalExitBlocks());
    while (!worklist.isEmpty()) {
      BasicBlock block = worklist.removeFirst();
      Return returnInstruction = block.entry().asReturn();
      if (returnInstruction == null) {
        continue;
      }
      Value returnValue = returnInstruction.getReturnValueOrNull();
      IntList predecessorsToRemove = new IntArrayList();
      for (int predecessorIndex = 0;
          predecessorIndex < block.getPredecessors().size();
          predecessorIndex++) {
        BasicBlock predecessor = block.getPredecessor(predecessorIndex);
        if (!predecessor.exit().isGoto() || predecessor.isMarked(color)) {
          continue;
        }
        if (predecessor.exit().asGoto().getTarget() != block) {
          assert predecessor.hasCatchSuccessor(block);
          continue;
        }
        if (block.hasCatchHandlers()) {
          for (BasicBlock catchHandlerBlock : block.getSuccessors()) {
            catchHandlerBlock.getMutablePredecessors().clear();
          }
          block.getMutableSuccessors().clear();
          block.clearCatchHandlers();
          hasUnreachableBlocks = true;
        } else {
          assert block.getSuccessors().isEmpty();
        }
        Value newReturnValue;
        if (returnValue != null && returnValue.isPhi() && returnValue.getBlock() == block) {
          newReturnValue = returnValue.asPhi().getOperand(predecessorIndex);
        } else {
          newReturnValue = returnValue;
        }
        Return newReturnInstruction =
            Return.builder().setReturnValue(newReturnValue).setPosition(returnInstruction).build();
        predecessor.exit().replace(newReturnInstruction);
        predecessor.removeAllNormalSuccessors();
        predecessorsToRemove.add(predecessorIndex);
        worklist.add(predecessor);
      }
      if (!predecessorsToRemove.isEmpty()) {
        if (predecessorsToRemove.size() == block.getPredecessors().size()) {
          blocksToRemove.add(block);
          for (Phi phi : block.getPhis()) {
            for (Value operand : phi.getOperands()) {
              operand.removePhiUser(phi);
            }
          }
          if (returnValue != null) {
            returnValue.removeUser(returnInstruction);
          }
        } else {
          block.removePredecessorsByIndex(predecessorsToRemove);
          block.removePhisByIndex(predecessorsToRemove);
          block.removeTrivialPhis(affectedValues);
          affectedValues.addAll(block.getPhis());
        }
        changed = true;
      }
    }
    code.removeBlocks(blocksToRemove);
    if (hasUnreachableBlocks) {
      code.removeUnreachableBlocks(affectedValues, emptyConsumer());
    }
    affectedValues.narrowingWithAssumeRemoval(appView, code);
    if (changed) {
      code.removeRedundantBlocks();
    }
    code.returnMarkingColor(color);
    return CodeRewriterResult.hasChanged(changed);
  }

  private int colorExceptionHandlers(IRCode code) {
    int color = code.reserveMarkingColor();
    WorkList<BasicBlock> worklist = WorkList.newIdentityWorkList();
    for (BasicBlock block : code.getBlocks()) {
      worklist.addIfNotSeen(block.getCatchHandlers().getUniqueTargets());
    }
    worklist.process(
        block -> {
          block.mark(color);
          worklist.addIfNotSeen(block.getSuccessors());
        });
    return color;
  }
}
