// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.optimize.argumentpropagation.utils.WideningUtils;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;
import com.android.tools.r8.utils.SetUtils;
import java.util.Collections;
import java.util.Set;

public class ConcreteClassTypeValueState extends ConcreteReferenceTypeValueState {

  private AbstractValue abstractValue;
  private DynamicType dynamicType;

  public ConcreteClassTypeValueState(InFlow inFlow) {
    this(AbstractValue.bottom(), DynamicType.bottom(), SetUtils.newHashSet(inFlow));
  }

  public ConcreteClassTypeValueState(AbstractValue abstractValue, DynamicType dynamicType) {
    this(abstractValue, dynamicType, Collections.emptySet());
  }

  public ConcreteClassTypeValueState(
      AbstractValue abstractValue, DynamicType dynamicType, Set<InFlow> inFlow) {
    super(inFlow);
    this.abstractValue = abstractValue;
    this.dynamicType = dynamicType;
    assert !isEffectivelyBottom() : "Must use BottomClassTypeParameterState instead";
    assert !isEffectivelyUnknown() : "Must use UnknownParameterState instead";
  }

  public static NonEmptyValueState create(AbstractValue abstractValue, DynamicType dynamicType) {
    return abstractValue.isUnknown() && dynamicType.isUnknown()
        ? ValueState.unknown()
        : new ConcreteClassTypeValueState(abstractValue, dynamicType);
  }

  @Override
  public ValueState clearInFlow() {
    if (hasInFlow()) {
      if (abstractValue.isBottom()) {
        assert dynamicType.isBottom();
        return bottomClassTypeParameter();
      }
      internalClearInFlow();
    }
    assert !isEffectivelyBottom();
    return this;
  }

  @Override
  public AbstractValue getAbstractValue(AppView<AppInfoWithLiveness> appView) {
    if (getDynamicType().getNullability().isDefinitelyNull()) {
      assert abstractValue.isNull() || abstractValue.isUnknown();
      return appView.abstractValueFactory().createUncheckedNullValue();
    }
    return abstractValue;
  }

  @Override
  public DynamicType getDynamicType() {
    return dynamicType;
  }

  @Override
  public Nullability getNullability() {
    return getDynamicType().getNullability();
  }

  @Override
  public ConcreteParameterStateKind getKind() {
    return ConcreteParameterStateKind.CLASS;
  }

  @Override
  public boolean isClassState() {
    return true;
  }

  @Override
  public ConcreteClassTypeValueState asClassState() {
    return this;
  }

  @Override
  public boolean isEffectivelyBottom() {
    return abstractValue.isBottom() && dynamicType.isBottom() && !hasInFlow();
  }

  @Override
  public boolean isEffectivelyUnknown() {
    return abstractValue.isUnknown() && dynamicType.isUnknown();
  }

  @Override
  public ValueState mutableCopy() {
    return new ConcreteClassTypeValueState(abstractValue, dynamicType, copyInFlow());
  }

  public NonEmptyValueState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      AbstractValue abstractValue,
      DynamicType dynamicType,
      ProgramField field) {
    assert field.getType().isClassType();
    mutableJoinAbstractValue(appView, abstractValue, field.getType());
    mutableJoinDynamicType(appView, dynamicType, field.getType());
    if (isEffectivelyUnknown()) {
      return unknown();
    }
    return this;
  }

  @Override
  public NonEmptyValueState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ConcreteReferenceTypeValueState state,
      DexType staticType,
      Action onChangedAction) {
    assert staticType.isClassType();
    boolean abstractValueChanged =
        mutableJoinAbstractValue(appView, state.getAbstractValue(appView), staticType);
    boolean dynamicTypeChanged =
        mutableJoinDynamicType(appView, state.getDynamicType(), staticType);
    if (isEffectivelyUnknown()) {
      return unknown();
    }
    boolean inFlowChanged = mutableJoinInFlow(state);
    if (widenInFlow(appView)) {
      return unknown();
    }
    if (abstractValueChanged || dynamicTypeChanged || inFlowChanged) {
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

  private boolean mutableJoinDynamicType(
      AppView<AppInfoWithLiveness> appView, DynamicType otherDynamicType, DexType staticType) {
    DynamicType oldDynamicType = dynamicType;
    DynamicType joinedDynamicType = dynamicType.join(appView, otherDynamicType);
    DynamicType widenedDynamicType =
        WideningUtils.widenDynamicNonReceiverType(appView, joinedDynamicType, staticType);
    dynamicType = widenedDynamicType;
    return !dynamicType.equals(oldDynamicType);
  }
}
