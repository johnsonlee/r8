// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.computation;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.AbstractFunction;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.BaseInFlow;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteClassTypeValueState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcretePrimitiveTypeValueState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteValueState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.FlowGraphStateProvider;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ValueState;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

/**
 * Represents a computation tree with no open variables other than the arguments of a given method.
 */
public interface ComputationTreeNode extends AbstractFunction {

  @Override
  default ValueState apply(
      AppView<AppInfoWithLiveness> appView,
      FlowGraphStateProvider flowGraphStateProvider,
      ConcreteValueState inState,
      DexType outStaticType) {
    AbstractValue abstractValue = evaluate(appView, flowGraphStateProvider);
    if (abstractValue.isBottom()) {
      return ValueState.bottom(outStaticType);
    } else if (abstractValue.isUnknown()) {
      return ValueState.unknown();
    } else {
      if (outStaticType.isArrayType()) {
        // We currently do not track abstract values for array types.
        return ValueState.unknown();
      } else if (outStaticType.isClassType()) {
        return ConcreteClassTypeValueState.create(abstractValue, DynamicType.unknown());
      } else {
        assert outStaticType.isPrimitiveType();
        return ConcretePrimitiveTypeValueState.create(abstractValue);
      }
    }
  }

  /** Evaluates the current computation tree on the given argument assignment. */
  AbstractValue evaluate(
      AppView<AppInfoWithLiveness> appView, FlowGraphStateProvider flowGraphStateProvider);

  BaseInFlow getSingleOpenVariable();

  @Override
  default boolean isAbstractComputation() {
    return true;
  }

  @Override
  default ComputationTreeNode asAbstractComputation() {
    return this;
  }

  default boolean isArgumentBitSetCompareNode() {
    return false;
  }

  boolean isComputationLeaf();

  @Override
  default boolean usesFlowGraphStateProvider() {
    return true;
  }

  default String toStringWithParenthesis() {
    if (isComputationLeaf()) {
      return toString();
    } else {
      return "(" + this + ")";
    }
  }
}
