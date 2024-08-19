// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.google.common.collect.Lists;

public class InstanceFieldReadAbstractFunction implements AbstractFunction {

  private final BaseInFlow receiver;
  private final DexField field;

  public InstanceFieldReadAbstractFunction(BaseInFlow receiver, DexField field) {
    this.receiver = receiver;
    this.field = field;
  }

  @Override
  public ValueState apply(
      AppView<AppInfoWithLiveness> appView,
      FlowGraphStateProvider flowGraphStateProvider,
      ConcreteValueState predecessorState) {
    ValueState state = flowGraphStateProvider.getState(receiver, () -> ValueState.bottom(field));
    if (state.isBottom()) {
      return ValueState.bottom(field);
    }
    if (!state.isClassState()) {
      return getFallbackState(flowGraphStateProvider);
    }
    ConcreteClassTypeValueState classState = state.asClassState();
    if (classState.getNullability().isDefinitelyNull()) {
      return ValueState.bottom(field);
    }
    AbstractValue abstractValue = state.getAbstractValue(null);
    if (!abstractValue.hasObjectState()) {
      return getFallbackState(flowGraphStateProvider);
    }
    AbstractValue fieldValue = abstractValue.getObjectState().getAbstractFieldValue(field);
    if (fieldValue.isUnknown()) {
      return getFallbackState(flowGraphStateProvider);
    }
    return ConcreteValueState.create(field.getType(), fieldValue);
  }

  @Override
  public boolean verifyContainsBaseInFlow(BaseInFlow inFlow) {
    assert inFlow.equals(receiver) || inFlow.isFieldValue(field);
    return true;
  }

  @Override
  public Iterable<BaseInFlow> getBaseInFlow() {
    return Lists.newArrayList(receiver, new FieldValue(field));
  }

  private ValueState getFallbackState(FlowGraphStateProvider flowGraphStateProvider) {
    ValueState valueState = flowGraphStateProvider.getState(new FieldValue(field), null);
    assert !valueState.isConcrete() || !valueState.asConcrete().hasInFlow();
    return valueState;
  }

  @Override
  public InFlowKind getKind() {
    return InFlowKind.ABSTRACT_FUNCTION_INSTANCE_FIELD_READ;
  }

  @Override
  public boolean usesFlowGraphStateProvider() {
    return true;
  }

  @Override
  public int internalCompareToSameKind(InFlow other, InFlowComparator comparator) {
    InstanceFieldReadAbstractFunction fn = other.asInstanceFieldReadAbstractFunction();
    int result = receiver.compareTo(fn.receiver, comparator);
    if (result == 0) {
      result = field.compareTo(fn.field);
    }
    return result;
  }

  @Override
  public boolean isInstanceFieldReadAbstractFunction() {
    return true;
  }

  @Override
  public InstanceFieldReadAbstractFunction asInstanceFieldReadAbstractFunction() {
    return this;
  }

  @Override
  public String toString() {
    return "Read(" + receiver + ", " + field.toSourceString() + ")";
  }
}
