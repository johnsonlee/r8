// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;
import com.android.tools.r8.utils.SetUtils;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

public class ConcretePrimitiveTypeValueState extends ConcreteValueState {

  private AbstractValue abstractValue;

  public ConcretePrimitiveTypeValueState(AbstractValue abstractValue) {
    this(abstractValue, Collections.emptySet());
  }

  public ConcretePrimitiveTypeValueState(AbstractValue abstractValue, Set<InFlow> inFlow) {
    super(inFlow);
    this.abstractValue = abstractValue;
    assert !isEffectivelyBottom() : "Must use BottomPrimitiveTypeParameterState instead";
    assert !isEffectivelyUnknown() : "Must use UnknownParameterState instead";
  }

  public ConcretePrimitiveTypeValueState(InFlow inFlow) {
    this(SetUtils.newHashSet(inFlow));
  }

  public ConcretePrimitiveTypeValueState(Set<InFlow> inFlow) {
    this(AbstractValue.bottom(), inFlow);
  }

  public static NonEmptyValueState create(AbstractValue abstractValue) {
    return create(abstractValue, Collections.emptySet());
  }

  public static NonEmptyValueState create(AbstractValue abstractValue, Set<InFlow> inFlow) {
    return abstractValue.isUnknown()
        ? ValueState.unknown()
        : new ConcretePrimitiveTypeValueState(abstractValue, inFlow);
  }

  @Override
  public ConcretePrimitiveTypeValueState internalMutableCopy(Supplier<Set<InFlow>> inFlowSupplier) {
    return new ConcretePrimitiveTypeValueState(abstractValue, inFlowSupplier.get());
  }

  public NonEmptyValueState mutableJoin(
      AppView<AppInfoWithLiveness> appView, ProgramField field, AbstractValue abstractValue) {
    mutableJoinAbstractValue(appView, abstractValue, field.getType());
    if (isEffectivelyUnknown()) {
      return unknown();
    }
    return this;
  }

  public NonEmptyValueState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ConcretePrimitiveTypeValueState state,
      DexType staticType,
      Action onChangedAction) {
    assert staticType.isPrimitiveType();
    boolean abstractValueChanged =
        mutableJoinAbstractValue(appView, state.getAbstractValue(), staticType);
    if (isEffectivelyUnknown()) {
      return unknown();
    }
    boolean inFlowChanged = mutableJoinInFlow(state);
    if (widenInFlow(appView)) {
      return unknown();
    }
    boolean unusedChanged = mutableJoinUnused(state);
    if (abstractValueChanged || inFlowChanged || unusedChanged) {
      onChangedAction.execute();
    }
    return this;
  }

  private boolean mutableJoinAbstractValue(
      AppView<AppInfoWithLiveness> appView, AbstractValue otherAbstractValue, DexType staticType) {
    AbstractValue oldAbstractValue = abstractValue;
    abstractValue =
        appView
            .getAbstractValueParameterJoiner()
            .join(abstractValue, otherAbstractValue, staticType);
    return !abstractValue.equals(oldAbstractValue);
  }

  public AbstractValue getAbstractValue() {
    return abstractValue;
  }

  @Override
  public AbstractValue getAbstractValue(AppView<AppInfoWithLiveness> appView) {
    return abstractValue;
  }

  @Override
  public BottomValueState getCorrespondingBottom() {
    return bottomPrimitiveTypeState();
  }

  @Override
  public UnusedValueState getCorrespondingUnused() {
    return unusedPrimitiveTypeState();
  }

  @Override
  public ConcreteParameterStateKind getKind() {
    return ConcreteParameterStateKind.PRIMITIVE;
  }

  @Override
  public boolean isEffectivelyBottomIgnoringInFlow() {
    return abstractValue.isBottom();
  }

  @Override
  public boolean isEffectivelyUnknown() {
    return abstractValue.isUnknown();
  }

  @Override
  public boolean isPrimitiveState() {
    return true;
  }

  @Override
  public ConcretePrimitiveTypeValueState asPrimitiveState() {
    return this;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof ConcretePrimitiveTypeValueState)) {
      return false;
    }
    ConcretePrimitiveTypeValueState state = (ConcretePrimitiveTypeValueState) obj;
    return abstractValue.equals(state.abstractValue) && getInFlow().equals(state.getInFlow());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getClass(), abstractValue, getInFlow());
  }

  @Override
  public String toString() {
    assert !hasInFlow();
    return "PrimitiveState(" + abstractValue + ")";
  }
}
