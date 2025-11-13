// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstanceGet;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.passes.CodeRewriterPass;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Set;

public class ShareInstanceGetInstructions extends CodeRewriterPass<AppInfo> {

  public ShareInstanceGetInstructions(AppView<?> appView) {
    super(appView);
  }

  @Override
  protected String getRewriterId() {
    return "ShareInstanceGetInstructions";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code, MethodProcessor methodProcessor) {
    return code.metadata().mayHaveInstanceGet();
  }

  @Override
  protected CodeRewriterResult rewriteCode(IRCode code) {
    boolean changed = false;
    for (BasicBlock block : code.getBlocks()) {
      // Try to hoist identical InstanceGets in two successors to this block:
      List<BasicBlock> successors = block.getSuccessors();
      // TODO(b/448586591: We should also be able to handle catch handlers by splitting the block we
      // hoist into.
      if (successors.size() == 2 && !block.hasCatchHandlers()) {
        InstanceGet firstInstanceGet = findFirstInstanceGetInstruction(code, successors.get(0));
        InstanceGet secondInstanceGet = findFirstInstanceGetInstruction(code, successors.get(1));
        if (firstInstanceGet == null || secondInstanceGet == null) {
          continue;
        }
        Value firstReceiver = firstInstanceGet.object();
        Value firstReceiverRoot = firstReceiver.getAliasedValue();

        Value secondReceiver = secondInstanceGet.object();
        DexField field = firstInstanceGet.getField();
        if (firstReceiverRoot != secondReceiver.getAliasedValue()
            || firstReceiver.isMaybeNull()
            || secondReceiver.isMaybeNull()
            || field.isNotIdenticalTo(secondInstanceGet.getField())) {
          continue;
        }
        Value firstOutValue = firstInstanceGet.outValue();
        Value secondOutValue = secondInstanceGet.outValue();
        if (firstOutValue.hasLocalInfo() || secondOutValue.hasLocalInfo()) {
          continue;
        }
        Value outValue = code.createValue(firstOutValue.getType());
        Value newReceiver =
            firstReceiver.getBlock() == firstInstanceGet.getBlock()
                ? firstReceiverRoot
                : firstReceiver;
        InstanceGet hoistedInstanceGet = new InstanceGet(outValue, newReceiver, field);
        hoistedInstanceGet.setPosition(firstInstanceGet.getPosition());
        block.getInstructions().addBefore(hoistedInstanceGet, block.getLastInstruction());
        removeOldInstructions(outValue, firstInstanceGet, secondInstanceGet);
        changed = true;
      }
      // Try to sink shareable InstanceGets from two predecessors into this block:
      List<BasicBlock> predecessors = block.getPredecessors();
      if (predecessors.size() == 2 && !block.hasCatchHandlers()) {
        BasicBlock firstPredecessor = predecessors.get(0);
        BasicBlock secondPredecessor = predecessors.get(1);
        // TODO(b/448586591: We should also be able to handle catch handlers by splitting the block
        // we hoist into.
        if (firstPredecessor.hasCatchHandlers() || secondPredecessor.hasCatchHandlers()) {
          continue;
        }
        InstanceGet firstInstanceGet = getLastInstanceGet(code, firstPredecessor);
        InstanceGet secondInstanceGet = getLastInstanceGet(code, secondPredecessor);
        if (firstInstanceGet == null || secondInstanceGet == null) {
          continue;
        }
        Value firstOutValue = firstInstanceGet.outValue();
        Value secondOutValue = secondInstanceGet.outValue();
        if (firstOutValue.hasLocalInfo()
            || secondOutValue.hasLocalInfo()
            || hasPhisThatWillBecomeInvalid(block, firstOutValue, secondOutValue)) {
          continue;
        }
        Value firstReceiver = firstInstanceGet.object();
        Value secondReceiver = secondInstanceGet.object();
        if (firstReceiver.isMaybeNull() || secondReceiver.isMaybeNull()) {
          continue;
        }
        DexField field = firstInstanceGet.getField();
        if (field.isNotIdenticalTo(secondInstanceGet.getField())) {
          continue;
        }
        Value receiver;
        if (firstReceiver == secondReceiver) {
          receiver = firstReceiver;
        } else {
          Value firstReceiverRoot = firstReceiver.getAliasedValue();
          if (firstReceiverRoot == secondReceiver.getAliasedValue()) {
            receiver = firstReceiverRoot;
          } else {
            TypeElement type = firstReceiver.getType().join(secondReceiver.getType(), appView);
            Phi phi = code.createPhi(block, type);
            phi.appendOperand(firstReceiver);
            phi.appendOperand(secondReceiver);
            receiver = phi;
          }
        }
        Value outValue = code.createValue(firstOutValue.getType());
        Instruction hoistedInstanceGet = new InstanceGet(outValue, receiver, field);
        hoistedInstanceGet.setPosition(firstInstanceGet.getPosition());
        block.getInstructions().addFirst(hoistedInstanceGet);
        removeOldInstructions(outValue, firstInstanceGet, secondInstanceGet);
        changed = true;
      }
    }
    if (changed) {
      code.removeRedundantBlocks();
    }
    return CodeRewriterResult.hasChanged(changed);
  }

  private static void removeOldInstructions(
      Value outValue, InstanceGet firstInstanceGet, InstanceGet secondInstanceGet) {
    firstInstanceGet.outValue().replaceUsers(outValue);
    secondInstanceGet.outValue().replaceUsers(outValue);
    outValue.uniquePhiUsers().forEach(Phi::removeTrivialPhi);
    firstInstanceGet.removeOrReplaceByDebugLocalRead();
    secondInstanceGet.removeOrReplaceByDebugLocalRead();
  }

  private boolean hasPhisThatWillBecomeInvalid(
      BasicBlock block, Value firstOutValue, Value secondOutValue) {
    for (Phi phi : block.getPhis()) {
      if (phi.getOperands().contains(firstOutValue)) {
        if (phi.getOperands().size() != 2 || !phi.getOperands().contains(secondOutValue)) {
          return true;
        }
      } else if (phi.getOperands().contains(secondOutValue)) {
        if (phi.getOperands().size() != 2 || !phi.getOperands().contains(firstOutValue)) {
          return true;
        }
      }
    }
    return false;
  }

  private InstanceGet getLastInstanceGet(IRCode code, BasicBlock block) {
    Set<Value> seenValues = Sets.newIdentityHashSet();
    for (Instruction instruction = block.getLastInstruction();
        instruction != null;
        instruction = instruction.getPrev()) {
      if (instruction.instructionMayHaveSideEffects(appView, code.context())) {
        break;
      }
      if (instruction.isInstanceGet() && !seenValues.contains(instruction.outValue())) {
        return instruction.asInstanceGet();
      } else {
        seenValues.addAll(instruction.inValues());
      }
    }
    return null;
  }

  private InstanceGet findFirstInstanceGetInstruction(IRCode code, BasicBlock block) {
    for (Instruction instruction = block.entry();
        instruction != null;
        instruction = instruction.getNext()) {
      if (instruction.instructionMayHaveSideEffects(appView, code.context())) {
        break;
      }
      if (instruction.isInstanceGet()) {
        return instruction.asInstanceGet();
      }
    }
    return null;
  }
}
