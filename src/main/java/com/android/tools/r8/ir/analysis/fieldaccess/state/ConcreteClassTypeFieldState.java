// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldaccess.state;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteReferenceTypeValueState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.InFlow;
import com.android.tools.r8.optimize.argumentpropagation.utils.WideningUtils;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;
import com.android.tools.r8.utils.SetUtils;
import java.util.Collections;
import java.util.Set;

/** The information that we track for fields whose type is a class type. */
public class ConcreteClassTypeFieldState extends ConcreteReferenceTypeFieldState {

  private DynamicType dynamicType;

  public ConcreteClassTypeFieldState(InFlow inFlow) {
    this(SetUtils.newHashSet(inFlow));
  }

  private ConcreteClassTypeFieldState(Set<InFlow> inFlow) {
    this(AbstractValue.bottom(), DynamicType.bottom(), inFlow);
  }

  @SuppressWarnings("InconsistentOverloads")
  private ConcreteClassTypeFieldState(
      AbstractValue abstractValue, DynamicType dynamicType, Set<InFlow> inFlow) {
    super(abstractValue, inFlow);
    this.dynamicType = dynamicType;
  }

  public static NonEmptyFieldState create(AbstractValue abstractValue, DynamicType dynamicType) {
    return create(abstractValue, dynamicType, Collections.emptySet());
  }

  public static NonEmptyFieldState create(
      AbstractValue abstractValue, DynamicType dynamicType, Set<InFlow> inFlow) {
    return abstractValue.isUnknown() && dynamicType.isUnknown()
        ? FieldState.unknown()
        : new ConcreteClassTypeFieldState(abstractValue, dynamicType, inFlow);
  }

  @Override
  public DynamicType getDynamicType() {
    return dynamicType;
  }

  @Override
  public boolean isClass() {
    return true;
  }

  @Override
  public ConcreteClassTypeFieldState asClass() {
    return this;
  }

  @Override
  public boolean isEffectivelyBottom() {
    return super.isEffectivelyBottom() && dynamicType.isBottom();
  }

  @Override
  public boolean isEffectivelyUnknown() {
    return super.isEffectivelyUnknown() && dynamicType.isUnknown();
  }

  @Override
  public FieldState mutableCopy() {
    return new ConcreteClassTypeFieldState(getAbstractValue(), getDynamicType(), copyInFlow());
  }

  public FieldState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      AbstractValue abstractValue,
      DynamicType dynamicType,
      ProgramField field) {
    assert field.getType().isClassType();
    mutableJoinAbstractValue(appView, field, abstractValue);
    mutableJoinDynamicType(appView, field, dynamicType);
    if (isEffectivelyUnknown()) {
      return unknown();
    }
    return this;
  }

  @Override
  public FieldState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ProgramField field,
      ConcreteReferenceTypeFieldState fieldState,
      Action onChangedAction) {
    boolean abstractValueChanged = mutableJoinAbstractValue(appView, field, fieldState);
    boolean dynamicTypeChanged = mutableJoinDynamicType(appView, field, fieldState);
    if (isEffectivelyUnknown()) {
      return unknown();
    }
    boolean inFlowChanged = mutableJoinInFlow(fieldState);
    if (widenInFlow(appView)) {
      return unknown();
    }
    if (abstractValueChanged || dynamicTypeChanged || inFlowChanged) {
      onChangedAction.execute();
    }
    return this;
  }

  @Override
  public FieldState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ProgramField field,
      ConcreteReferenceTypeValueState parameterState,
      Action onChangedAction) {
    boolean abstractValueChanged =
        mutableJoinAbstractValue(appView, field, parameterState.getAbstractValue(appView));
    boolean dynamicTypeChanged =
        mutableJoinDynamicType(appView, field, parameterState.getDynamicType());
    if (isEffectivelyUnknown()) {
      return unknown();
    }
    boolean inFlowChanged = mutableJoinInFlow(parameterState.getInFlow());
    if (widenInFlow(appView)) {
      return unknown();
    }
    if (abstractValueChanged || dynamicTypeChanged || inFlowChanged) {
      onChangedAction.execute();
    }
    return this;
  }

  private boolean mutableJoinDynamicType(
      AppView<AppInfoWithLiveness> appView,
      ProgramField field,
      ConcreteReferenceTypeFieldState fieldState) {
    return mutableJoinDynamicType(
        appView,
        field,
        fieldState.isClass() ? fieldState.asClass().getDynamicType() : DynamicType.unknown());
  }

  private boolean mutableJoinDynamicType(
      AppView<AppInfoWithLiveness> appView, ProgramField field, DynamicType otherDynamicType) {
    DynamicType oldDynamicType = dynamicType;
    DynamicType joinedDynamicType = dynamicType.join(appView, otherDynamicType);
    DynamicType widenedDynamicType =
        WideningUtils.widenDynamicNonReceiverType(appView, joinedDynamicType, field.getType());
    dynamicType = widenedDynamicType;
    return !dynamicType.equals(oldDynamicType);
  }
}
