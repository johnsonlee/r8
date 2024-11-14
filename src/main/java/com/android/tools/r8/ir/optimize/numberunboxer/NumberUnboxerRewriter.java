// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.numberunboxer;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.lens.NonIdentityGraphLens;
import com.android.tools.r8.graph.proto.ArgumentInfo;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.graph.proto.RewrittenTypeInfo;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Return;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.optimize.CustomLensCodeRewriter;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class NumberUnboxerRewriter implements CustomLensCodeRewriter {

  private final AppView<AppInfoWithLiveness> appView;

  public NumberUnboxerRewriter(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  @Override
  public Set<Phi> rewriteCode(
      IRCode code,
      MethodProcessor methodProcessor,
      RewrittenPrototypeDescription prototypeChanges,
      NonIdentityGraphLens graphLens) {
    assert graphLens.isNumberUnboxerLens();
    Set<Phi> affectedPhis = Sets.newIdentityHashSet();
    rewriteArgs(code, prototypeChanges, affectedPhis);
    InstructionListIterator iterator = code.instructionListIterator();
    while (iterator.hasNext()) {
      Instruction next = iterator.next();
      if (next.isInvokeMethod()) {
        InvokeMethod invokeMethod = next.asInvokeMethod();
        // TODO(b/314117865): This is assuming that there are no non-rebound method references.
        DexMethod rewrittenMethod =
            graphLens
                .lookupMethod(
                    invokeMethod.getInvokedMethod(),
                    code.context().getReference(),
                    invokeMethod.getType(),
                    graphLens.getPrevious())
                .getReference();
        assert rewrittenMethod != null;
        RewrittenPrototypeDescription rewrittenPrototypeDescription =
            graphLens.lookupPrototypeChangesForMethodDefinition(
                rewrittenMethod, graphLens.getPrevious());
        if (!rewrittenPrototypeDescription.isEmpty()) {
          unboxInvokeValues(
              code, iterator, invokeMethod, rewrittenPrototypeDescription, affectedPhis);
        }
      } else if (next.isReturn() && next.asReturn().hasReturnValue()) {
        unboxReturnIfNeeded(code, iterator, next.asReturn(), prototypeChanges);
      }
    }
    return affectedPhis;
  }

  private void unboxInvokeValues(
      IRCode code,
      InstructionListIterator iterator,
      InvokeMethod invokeMethod,
      RewrittenPrototypeDescription prototypeChanges,
      Set<Phi> affectedPhis) {
    assert prototypeChanges.getArgumentInfoCollection().numberOfRemovedArguments() == 0;
    for (int inValueIndex = 0; inValueIndex < invokeMethod.inValues().size(); inValueIndex++) {
      ArgumentInfo argumentInfo =
          prototypeChanges.getArgumentInfoCollection().getArgumentInfo(inValueIndex);
      if (argumentInfo.isRewrittenTypeInfo()) {
        Value invokeArg = invokeMethod.getArgument(inValueIndex);
        InvokeVirtual unboxOperation =
            computeUnboxInvokeIfNeeded(
                code, invokeArg, argumentInfo.asRewrittenTypeInfo(), invokeMethod.getPosition());
        if (unboxOperation != null) {
          iterator.addBeforeAndPositionAfterNewInstruction(unboxOperation);
          invokeMethod.replaceValue(inValueIndex, unboxOperation.outValue());
        }
      }
    }
    if (invokeMethod.hasOutValue()) {
      InvokeStatic boxOperation =
          computeBoxInvokeIfNeeded(
              code,
              invokeMethod.outValue(),
              prototypeChanges.getRewrittenReturnInfo(),
              invokeMethod.getPosition());
      if (boxOperation != null) {
        iterator.add(boxOperation);
        affectedPhis.addAll(boxOperation.outValue().uniquePhiUsers());
      }
    }
  }

  private void unboxReturnIfNeeded(
      IRCode code,
      InstructionListIterator iterator,
      Return ret,
      RewrittenPrototypeDescription prototypeChanges) {
    InvokeVirtual unbox =
        computeUnboxInvokeIfNeeded(
            code, ret.returnValue(), prototypeChanges.getRewrittenReturnInfo(), ret.getPosition());
    if (unbox != null) {
      iterator.addBeforeAndPositionAfterNewInstruction(unbox);
      ret.replaceValue(ret.returnValue(), unbox.outValue());
    }
  }

  private InvokeVirtual computeUnboxInvokeIfNeeded(
      IRCode code, Value input, RewrittenTypeInfo rewrittenTypeInfo, Position pos) {
    if (rewrittenTypeInfo == null) {
      return null;
    }
    assert rewrittenTypeInfo.getOldType().isReferenceType();
    assert rewrittenTypeInfo.getNewType().isPrimitiveType();
    assert appView.dexItemFactory().primitiveToBoxed.containsValue(rewrittenTypeInfo.getOldType());
    return InvokeVirtual.builder()
        .setMethod(appView.dexItemFactory().getUnboxPrimitiveMethod(rewrittenTypeInfo.getNewType()))
        .setFreshOutValue(
            code.valueNumberGenerator, rewrittenTypeInfo.getNewType().toTypeElement(appView))
        .setArguments(ImmutableList.of(input))
        .setPosition(pos)
        .build();
  }

  private InvokeStatic computeBoxInvokeIfNeeded(
      IRCode code, Value output, RewrittenTypeInfo rewrittenTypeInfo, Position pos) {
    if (rewrittenTypeInfo == null) {
      return null;
    }
    assert rewrittenTypeInfo.getOldType().isReferenceType();
    assert rewrittenTypeInfo.getNewType().isPrimitiveType();
    assert appView.dexItemFactory().primitiveToBoxed.containsValue(rewrittenTypeInfo.getOldType());
    Value outValue = code.createValue(rewrittenTypeInfo.getOldType().toTypeElement(appView));
    output.replaceUsers(outValue);
    return InvokeStatic.builder()
        .setOutValue(outValue)
        .setMethod(appView.dexItemFactory().getBoxPrimitiveMethod(rewrittenTypeInfo.getNewType()))
        .setSingleArgument(output)
        .setPosition(pos)
        .build();
  }

  private void rewriteArgs(
      IRCode code, RewrittenPrototypeDescription prototypeChanges, Set<Phi> affectedPhis) {
    List<InvokeStatic> boxingOperations = new ArrayList<>();
    BasicBlock basicBlock = code.entryBlock();
    InstructionListIterator iterator = basicBlock.listIterator();
    Position pos = iterator.peekNext().getPosition();
    assert prototypeChanges.getArgumentInfoCollection().numberOfRemovedArguments() == 0;
    int originalNumberOfArguments = code.getNumberOfArguments();
    for (int argumentIndex = 0; argumentIndex < originalNumberOfArguments; argumentIndex++) {
      ArgumentInfo argumentInfo =
          prototypeChanges.getArgumentInfoCollection().getArgumentInfo(argumentIndex);
      Instruction next = iterator.next();
      assert next.isArgument();
      if (argumentInfo.isRewrittenTypeInfo()) {
        InvokeStatic boxOperation =
            computeBoxInvokeIfNeeded(
                code, next.outValue(), argumentInfo.asRewrittenTypeInfo(), pos);
        if (boxOperation != null) {
          boxingOperations.add(boxOperation);
        }
      }
    }
    assert !iterator.peekNext().isArgument();
    for (InvokeStatic boxingOp : boxingOperations) {
      iterator.add(boxingOp);
      affectedPhis.addAll(boxingOp.outValue().uniquePhiUsers());
    }
  }
}
