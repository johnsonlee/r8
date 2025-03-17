// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion.passes;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Return;
import com.android.tools.r8.ir.code.Switch;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Finds different return blocks that return the same value and then rewrites all blocks that jump
 * to such a return block into a canonical return block.
 */
public class ReturnBlockCanonicalizerRewriter extends CodeRewriterPass<AppInfo> {

  private final Value VOID_RETURN_VALUE_SENTINEL =
      Value.createNoDebugLocal(-1, TypeElement.getTop());

  public ReturnBlockCanonicalizerRewriter(AppView<?> appView) {
    super(appView);
  }

  @Override
  protected String getRewriterId() {
    return "ReturnBlockCanonicalizerRewriter";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code, MethodProcessor methodProcessor) {
    assert options.isRelease();
    return options.getTestingOptions().enableDeadSwitchCaseElimination;
  }

  @Override
  protected CodeRewriterResult rewriteCode(IRCode code) {
    boolean changed = false;
    Map<Value, BasicBlock> returnValueToReturnBlockMap = getReturnValueToReturnBlockMap(code);
    for (BasicBlock returnBlock : code.getBlocks()) {
      Return theReturn = getReturnFromEligibleBlock(returnBlock);
      if (theReturn == null) {
        continue;
      }
      Value returnValue = theReturn.getReturnValueOrDefault(VOID_RETURN_VALUE_SENTINEL);
      BasicBlock replacementBlock =
          returnValueToReturnBlockMap.getOrDefault(returnValue, returnBlock);
      if (replacementBlock == returnBlock) {
        continue;
      }
      for (BasicBlock predecessor : returnBlock.getPredecessors()) {
        predecessor.replaceSuccessor(returnBlock, replacementBlock);
        if (!replacementBlock.getPredecessors().contains(predecessor)) {
          replacementBlock.getMutablePredecessors().add(predecessor);
        }
        if (predecessor.exit().isSwitch()) {
          Switch theSwitch = predecessor.exit().asSwitch();
          if (theSwitch.fallthroughBlock() == replacementBlock) {
            SwitchCaseEliminator eliminator = new SwitchCaseEliminator(theSwitch);
            for (int i = 0; i < theSwitch.numberOfKeys(); i++) {
              if (theSwitch.getTargetBlockIndex(i) == theSwitch.getFallthroughBlockIndex()) {
                eliminator.markSwitchCaseForRemoval(i);
              }
            }
            eliminator.optimize();
          }
        }
      }
      returnBlock.getMutablePredecessors().clear();
      changed = true;
    }
    if (changed) {
      code.removeUnreachableBlocks();
      code.splitCriticalEdges();
      code.removeRedundantBlocks();
    }
    return CodeRewriterResult.hasChanged(changed);
  }

  private Map<Value, BasicBlock> getReturnValueToReturnBlockMap(IRCode code) {
    Map<Value, BasicBlock> returnValueToReturnBlockMap = new IdentityHashMap<>();
    for (BasicBlock returnBlock : code.getBlocks()) {
      Return theReturn = getReturnFromEligibleBlock(returnBlock);
      if (theReturn == null) {
        continue;
      }
      Value returnValue = theReturn.getReturnValueOrDefault(VOID_RETURN_VALUE_SENTINEL);
      returnValueToReturnBlockMap.putIfAbsent(returnValue, returnBlock);
    }
    return returnValueToReturnBlockMap;
  }

  private Return getReturnFromEligibleBlock(BasicBlock block) {
    Return theReturn = block.entry().asReturn();
    if (theReturn == null || block.hasPhis()) {
      return null;
    }
    if (block.hasUniquePredecessor() && block.getUniquePredecessor().hasCatchSuccessor(block)) {
      return null;
    }
    return theReturn;
  }
}
