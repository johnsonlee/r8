// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.optimize.info.MethodOptimizationInfo;
import com.android.tools.r8.optimize.argumentpropagation.propagation.FlowGraph;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import java.util.function.Supplier;

public interface FlowGraphStateProvider {

  static FlowGraphStateProvider create(FlowGraph flowGraph, AbstractFunction abstractFunction) {
    if (!InternalOptions.assertionsEnabled()) {
      return flowGraph;
    }
    // If the abstract function needs to perform state lookups, we restrict state lookups to the
    // declared base in flow. This is important for arriving at the correct fix point.
    if (abstractFunction.usesFlowGraphStateProvider()) {
      assert abstractFunction.isAbstractComputation()
          || abstractFunction.isIfThenElseAbstractFunction()
          || abstractFunction.isInstanceFieldReadAbstractFunction();
      return new FlowGraphStateProvider() {

        @Override
        public ValueState getState(DexField field) {
          assert abstractFunction.verifyContainsBaseInFlow(new FieldValue(field));
          return flowGraph.getState(field);
        }

        @Override
        public ValueState getState(
            MethodParameter methodParameter, Supplier<ValueState> defaultStateProvider) {
          assert abstractFunction.verifyContainsBaseInFlow(methodParameter);
          return flowGraph.getState(methodParameter, defaultStateProvider);
        }
      };
    }
    // Otherwise, the abstract function is a canonical function, or the abstract function has a
    // single declared input, meaning we should never perform any state lookups.
    assert abstractFunction.isIdentity() || abstractFunction.isCastAbstractFunction();
    return new FlowGraphStateProvider() {

      @Override
      public ValueState getState(DexField field) {
        throw new Unreachable();
      }

      @Override
      public ValueState getState(
          MethodParameter methodParameter, Supplier<ValueState> defaultStateProvider) {
        throw new Unreachable();
      }
    };
  }

  static FlowGraphStateProvider createFromMethodOptimizationInfo(ProgramMethod method) {
    return new FlowGraphStateProvider() {

      @Override
      public ValueState getState(DexField field) {
        return ValueState.unknown();
      }

      @Override
      public ValueState getState(
          MethodParameter methodParameter, Supplier<ValueState> defaultStateProvider) {
        if (methodParameter.getMethod().isNotIdenticalTo(method.getReference())) {
          return ValueState.unknown();
        }
        MethodOptimizationInfo optimizationInfo = method.getOptimizationInfo();
        AbstractValue abstractValue =
            optimizationInfo.getArgumentInfos().getAbstractArgumentValue(methodParameter);
        if (abstractValue.isUnknown()) {
          return ValueState.unknown();
        }
        return ConcreteValueState.create(methodParameter.getType(), abstractValue);
      }
    };
  }

  static FlowGraphStateProvider createFromInvoke(
      AppView<AppInfoWithLiveness> appView,
      InvokeMethod invoke,
      ProgramMethod singleTarget,
      ProgramMethod context) {
    return new FlowGraphStateProvider() {

      @Override
      public ValueState getState(DexField field) {
        return ValueState.unknown();
      }

      @Override
      public ValueState getState(
          MethodParameter methodParameter, Supplier<ValueState> defaultStateProvider) {
        if (methodParameter.getMethod().isNotIdenticalTo(singleTarget.getReference())) {
          return ValueState.unknown();
        }
        AbstractValue abstractValue =
            invoke.getArgument(methodParameter.getIndex()).getAbstractValue(appView, context);
        if (abstractValue.isUnknown()) {
          return ValueState.unknown();
        }
        return ConcreteValueState.create(methodParameter.getType(), abstractValue);
      }
    };
  }

  ValueState getState(DexField field);

  ValueState getState(MethodParameter methodParameter, Supplier<ValueState> defaultStateProvider);

  default ValueState getState(BaseInFlow inFlow, Supplier<ValueState> defaultStateProvider) {
    if (inFlow.isFieldValue()) {
      return getState(inFlow.asFieldValue().getField());
    } else {
      assert inFlow.isMethodParameter();
      return getState(inFlow.asMethodParameter(), defaultStateProvider);
    }
  }
}
