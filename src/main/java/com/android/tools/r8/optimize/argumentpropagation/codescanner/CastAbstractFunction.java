// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.TraversalContinuation;
import java.util.Objects;
import java.util.function.Function;

public class CastAbstractFunction implements AbstractFunction {

  private final BaseInFlow inFlow;
  private final DexType type;

  public CastAbstractFunction(BaseInFlow inFlow, DexType type) {
    this.inFlow = inFlow;
    this.type = type;
  }

  @Override
  public ValueState apply(
      AppView<AppInfoWithLiveness> appView,
      FlowGraphStateProvider flowGraphStateProvider,
      ConcreteValueState predecessorState,
      DexType outStaticType) {
    return predecessorState.asReferenceState().cast(appView, type);
  }

  @Override
  public <TB, TC> TraversalContinuation<TB, TC> traverseBaseInFlow(
      Function<? super BaseInFlow, TraversalContinuation<TB, TC>> fn) {
    return inFlow.traverseBaseInFlow(fn);
  }

  @Override
  public InFlowKind getKind() {
    return InFlowKind.ABSTRACT_FUNCTION_CAST;
  }

  @Override
  public int internalCompareToSameKind(InFlow other, InFlowComparator comparator) {
    CastAbstractFunction fn = other.asCastAbstractFunction();
    if (inFlow != fn.inFlow) {
      int result = inFlow.compareTo(fn.inFlow, comparator);
      assert result != 0;
      return result;
    }
    return type.compareTo(fn.type);
  }

  @Override
  public boolean isCastAbstractFunction() {
    return true;
  }

  @Override
  public CastAbstractFunction asCastAbstractFunction() {
    return this;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof CastAbstractFunction)) {
      return false;
    }
    CastAbstractFunction fn = (CastAbstractFunction) obj;
    return inFlow.equals(fn.inFlow) && type.isIdenticalTo(fn.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(inFlow, type);
  }

  @Override
  public String toString() {
    return "Cast(" + inFlow + ", " + type.getTypeName() + ")";
  }
}
