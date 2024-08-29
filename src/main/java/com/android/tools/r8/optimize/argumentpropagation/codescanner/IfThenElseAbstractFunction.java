// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.code.Position.SourcePosition;
import com.android.tools.r8.optimize.argumentpropagation.computation.ComputationTreeNode;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.TraversalContinuation;
import java.util.Objects;
import java.util.function.Function;

/**
 * Represents a ternary expression (exp ? u : v). The {@link #condition} is an expression containing
 * a single open variable which evaluates to an abstract value. If the resulting abstract value is
 * true, then `u` is chosen. If the abstract value is false, then `v` is chosen. Otherwise, the
 * result is unknown.
 */
// TODO(b/302281503): Replace this by a ComputationTreeNode.
// TODO(b/302281503): Evaluate the impact of using the join of `u` and `v` instead of unknown when
//  the condition does not evaluate to true or false.
public class IfThenElseAbstractFunction implements AbstractFunction {

  private final ComputationTreeNode condition;
  private final NonEmptyValueState thenState;
  private final NonEmptyValueState elseState;

  public IfThenElseAbstractFunction(
      ComputationTreeNode condition, NonEmptyValueState thenState, NonEmptyValueState elseState) {
    assert condition.getSingleOpenVariable() != null;
    assert !thenState.isUnknown() || !elseState.isUnknown();
    assert !thenState.isConcrete() || thenState.asConcrete().verifyOnlyBaseInFlow();
    assert !elseState.isConcrete() || elseState.asConcrete().verifyOnlyBaseInFlow();
    this.condition = condition;
    this.thenState = thenState;
    this.elseState = elseState;
  }

  @Override
  public ValueState apply(
      AppView<AppInfoWithLiveness> appView,
      FlowGraphStateProvider flowGraphStateProvider,
      ConcreteValueState inState,
      DexType outStaticType) {
    AbstractValue conditionValue = condition.evaluate(appView, flowGraphStateProvider);
    NonEmptyValueState resultState;
    if (conditionValue.isTrue()) {
      resultState = thenState;
    } else if (conditionValue.isFalse()) {
      resultState = elseState;
    } else {
      return ValueState.unknown();
    }
    if (resultState.isUnknown()) {
      return resultState;
    }
    assert resultState.isConcrete();
    ConcreteValueState concreteResultState = resultState.asConcrete();
    if (!concreteResultState.hasInFlow()) {
      return concreteResultState;
    }
    return resolveInFlow(appView, flowGraphStateProvider, concreteResultState, outStaticType);
  }

  private ValueState resolveInFlow(
      AppView<AppInfoWithLiveness> appView,
      FlowGraphStateProvider flowGraphStateProvider,
      ConcreteValueState resultStateWithInFlow,
      DexType outStaticType) {
    ValueState resultStateWithoutInFlow = resultStateWithInFlow.mutableCopyWithoutInFlow();
    for (InFlow inFlow : resultStateWithInFlow.getInFlow()) {
      // We currently only allow the primitive kinds of in flow (fields and method parameters) to
      // occur in the states.
      assert inFlow.isBaseInFlow();
      ValueState inFlowState = flowGraphStateProvider.getState(inFlow.asBaseInFlow(), () -> null);
      if (inFlowState == null) {
        return ValueState.unknown();
      }
      // TODO(b/302281503): The IfThenElseAbstractFunction is only used on input to base in flow.
      //  We should set  the `outStaticType` to the static type of the current field/parameter.
      DexType inStaticType = null;
      resultStateWithoutInFlow =
          resultStateWithoutInFlow.mutableJoin(
              appView, inFlowState, inStaticType, outStaticType, StateCloner.getCloner());
    }
    return resultStateWithoutInFlow;
  }

  @Override
  public <TB, TC> TraversalContinuation<TB, TC> traverseBaseInFlow(
      Function<? super BaseInFlow, TraversalContinuation<TB, TC>> fn) {
    TraversalContinuation<TB, TC> traversalContinuation = condition.traverseBaseInFlow(fn);
    if (traversalContinuation.shouldBreak()) {
      return traversalContinuation;
    }
    if (thenState.isConcrete()) {
      traversalContinuation = thenState.asConcrete().traverseBaseInFlow(fn);
      if (traversalContinuation.shouldBreak()) {
        return traversalContinuation;
      }
    }
    if (elseState.isConcrete()) {
      traversalContinuation = elseState.asConcrete().traverseBaseInFlow(fn);
      if (traversalContinuation.shouldBreak()) {
        return traversalContinuation;
      }
    }
    return traversalContinuation;
  }

  @Override
  public boolean usesFlowGraphStateProvider() {
    return true;
  }

  @Override
  public int internalCompareToSameKind(InFlow inFlow, InFlowComparator comparator) {
    SourcePosition position = comparator.getIfThenElsePosition(this);
    SourcePosition otherPosition =
        comparator.getIfThenElsePosition(inFlow.asIfThenElseAbstractFunction());
    return position.compareTo(otherPosition);
  }

  @Override
  public boolean isIfThenElseAbstractFunction() {
    return true;
  }

  @Override
  public IfThenElseAbstractFunction asIfThenElseAbstractFunction() {
    return this;
  }

  @Override
  public InFlowKind getKind() {
    return InFlowKind.ABSTRACT_FUNCTION_IF_THEN_ELSE;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof IfThenElseAbstractFunction)) {
      return false;
    }
    IfThenElseAbstractFunction fn = (IfThenElseAbstractFunction) obj;
    return condition.equals(fn.condition)
        && thenState.equals(fn.thenState)
        && elseState.equals(fn.elseState);
  }

  @Override
  public int hashCode() {
    return Objects.hash(getClass(), condition, thenState, elseState);
  }
}
