// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.membervaluepropagation;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.ArrayGet;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.FieldInstruction;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstanceGet;
import com.android.tools.r8.ir.code.InstancePut;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.StaticPut;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.membervaluepropagation.assume.AssumeInfo;
import com.android.tools.r8.ir.optimize.membervaluepropagation.assume.AssumeInfoLookup;
import java.util.Set;

public class AssumePropagator extends MemberValuePropagation<AppInfoWithClassHierarchy> {

  public AssumePropagator(AppView<? extends AppInfoWithClassHierarchy> appView) {
    super(appView);
  }

  @Override
  InstructionListIterator rewriteInvokeMethod(
      IRCode code,
      ProgramMethod context,
      Set<Value> affectedValues,
      BasicBlockIterator blocks,
      InstructionListIterator iterator,
      InvokeMethod invoke) {
    if (invoke.hasUsedOutValue() && invoke.getInvokedMethod().getHolderType().isClassType()) {
      SingleResolutionResult<?> resolutionResult =
          invoke.resolveMethod(appView, context).asSingleResolution();
      if (resolutionResult != null) {
        DexClassAndMethod singleTarget = invoke.lookupSingleTarget(appView, context);
        AssumeInfo lookup =
            AssumeInfoLookup.lookupAssumeInfo(appView, resolutionResult, singleTarget);
        applyAssumeInfo(code, affectedValues, blocks, iterator, invoke, lookup);
      }
    }
    return iterator;
  }

  @Override
  InstructionListIterator rewriteInstanceGet(
      IRCode code,
      Set<Value> affectedValues,
      BasicBlockIterator blocks,
      InstructionListIterator iterator,
      InstanceGet current) {
    return rewriteFieldGet(code, affectedValues, blocks, iterator, current);
  }

  @Override
  InstructionListIterator rewriteStaticGet(
      IRCode code,
      Set<Value> affectedValues,
      BasicBlockIterator blocks,
      InstructionListIterator iterator,
      StaticGet current) {
    return rewriteFieldGet(code, affectedValues, blocks, iterator, current);
  }

  private InstructionListIterator rewriteFieldGet(
      IRCode code,
      Set<Value> affectedValues,
      BasicBlockIterator blocks,
      InstructionListIterator iterator,
      FieldInstruction current) {
    DexClassAndField resolvedField =
        current.resolveField(appView, code.context()).getResolutionPair();
    if (resolvedField != null) {
      AssumeInfo lookup = appView.getAssumeInfoCollection().get(resolvedField);
      applyAssumeInfo(code, affectedValues, blocks, iterator, current, lookup);
    }
    return iterator;
  }

  @Override
  void rewriteArrayGet(
      IRCode code,
      Set<Value> affectedValues,
      BasicBlockIterator blocks,
      InstructionListIterator iterator,
      ArrayGet arrayGet) {
    // Intentionally empty.
  }

  @Override
  void rewriteInstancePut(IRCode code, InstructionListIterator iterator, InstancePut current) {
    // Intentionally empty.
  }

  @Override
  void rewriteStaticPut(IRCode code, InstructionListIterator iterator, StaticPut current) {
    // Intentionally empty.
  }
}
