// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.optimize.argumentpropagation.computation.ComputationTreeNode;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.TraversalContinuation;
import java.util.Objects;
import java.util.function.Function;

public class MethodParameter implements BaseInFlow, ComputationTreeNode {

  private final DexMethod method;
  private final int index;
  private final boolean isMethodStatic;

  public MethodParameter(DexClassAndMethod method, int index) {
    this(method.getReference(), index, method.getAccessFlags().isStatic());
  }

  public MethodParameter(DexMethod method, int index, boolean isMethodStatic) {
    this.method = method;
    this.index = index;
    this.isMethodStatic = isMethodStatic;
  }

  @Override
  public <TB, TC> TraversalContinuation<TB, TC> traverseBaseInFlow(
      Function<? super BaseInFlow, TraversalContinuation<TB, TC>> fn) {
    return fn.apply(this);
  }

  @Override
  public InFlowKind getKind() {
    return InFlowKind.METHOD_PARAMETER;
  }

  public DexMethod getMethod() {
    return method;
  }

  public int getIndex() {
    return index;
  }

  @Override
  public BaseInFlow getSingleOpenVariable() {
    return this;
  }

  public DexType getType() {
    return method.getArgumentType(index, isMethodStatic);
  }

  @Override
  public AbstractValue evaluate(
      AppView<AppInfoWithLiveness> appView, FlowGraphStateProvider flowGraphStateProvider) {
    ValueState state = flowGraphStateProvider.getState(this, () -> ValueState.bottom(getType()));
    return state.getAbstractValue(appView);
  }

  @Override
  public int internalCompareToSameKind(InFlow other, InFlowComparator comparator) {
    MethodParameter methodParameter = other.asMethodParameter();
    int result = method.compareTo(methodParameter.method);
    if (result == 0) {
      result = index - methodParameter.index;
    }
    if (result == 0) {
      result =
          BooleanUtils.intValue(isMethodStatic)
              - BooleanUtils.intValue(methodParameter.isMethodStatic);
    }
    return result;
  }

  @Override
  public boolean isComputationLeaf() {
    return true;
  }

  @Override
  public boolean isMethodParameter() {
    return true;
  }

  @Override
  public boolean isMethodParameter(DexMethod method, int parameterIndex) {
    return this.method.isIdenticalTo(method) && this.index == parameterIndex;
  }

  @Override
  public MethodParameter asMethodParameter() {
    return this;
  }

  @Override
  @SuppressWarnings("EqualsGetClass")
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    MethodParameter methodParameter = (MethodParameter) obj;
    return method.isIdenticalTo(methodParameter.method) && index == methodParameter.index;
  }

  @Override
  public int hashCode() {
    return Objects.hash(method, index);
  }

  @Override
  public String toString() {
    return "MethodParameter(" + method + ", " + index + ")";
  }
}
