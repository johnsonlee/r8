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
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

public class ConcreteClassTypeValueState extends ConcreteReferenceTypeValueState {

  private AbstractValue abstractValue;
  private DynamicType dynamicType;

  public ConcreteClassTypeValueState(InFlow inFlow) {
    this(SetUtils.newHashSet(inFlow));
  }

  public ConcreteClassTypeValueState(Set<InFlow> inFlow) {
    this(AbstractValue.bottom(), DynamicType.bottom(), inFlow);
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
    return create(abstractValue, dynamicType, Collections.emptySet());
  }

  public static NonEmptyValueState create(
      AbstractValue abstractValue, DynamicType dynamicType, Set<InFlow> inFlow) {
    return abstractValue.isUnknown() && dynamicType.isUnknown()
        ? ValueState.unknown()
        : new ConcreteClassTypeValueState(abstractValue, dynamicType, inFlow);
  }

  @Override
  public ValueState cast(AppView<AppInfoWithLiveness> appView, DexType type) {
    DynamicType castDynamicType = cast(appView, type, dynamicType);
    if (castDynamicType.equals(dynamicType)) {
      return this;
    }
    if (castDynamicType.isBottom()) {
      return bottomClassTypeState();
    }
    assert castDynamicType.isDynamicTypeWithUpperBound();
    return new ConcreteClassTypeValueState(abstractValue, castDynamicType, copyInFlow());
  }

  @Override
  public AbstractValue getAbstractValue(AppView<AppInfoWithLiveness> appView) {
    if (getNullability().isDefinitelyNull()) {
      assert abstractValue.isNull()
          || abstractValue.isNullOrAbstractValue()
          || abstractValue.isUnknown();
      return appView.abstractValueFactory().createUncheckedNullValue();
    }
    return abstractValue;
  }

  @Override
  public BottomValueState getCorrespondingBottom() {
    return bottomClassTypeState();
  }

  @Override
  public UnusedValueState getCorrespondingUnused() {
    return unusedClassTypeState();
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
  public boolean isEffectivelyBottomIgnoringInFlow() {
    return abstractValue.isBottom() && dynamicType.isBottom();
  }

  @Override
  public boolean isEffectivelyUnknown() {
    return abstractValue.isUnknown() && dynamicType.isUnknown();
  }

  @Override
  public ConcreteClassTypeValueState internalMutableCopy(Supplier<Set<InFlow>> inFlowSupplier) {
    return new ConcreteClassTypeValueState(abstractValue, dynamicType, inFlowSupplier.get());
  }

  public NonEmptyValueState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      AbstractValue abstractValue,
      DynamicType inDynamicType,
      DexType inStaticType,
      ProgramField field) {
    assert field.getType().isClassType();
    mutableJoinAbstractValue(appView, abstractValue, field.getType());
    mutableJoinDynamicType(appView, inDynamicType, inStaticType, field.getType());
    if (isEffectivelyUnknown()) {
      return unknown();
    }
    return this;
  }

  @Override
  public NonEmptyValueState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ConcreteReferenceTypeValueState inState,
      DexType inStaticType,
      DexType outStaticType,
      Action onChangedAction) {
    assert outStaticType.isClassType();
    boolean abstractValueChanged =
        mutableJoinAbstractValue(appView, inState.getAbstractValue(appView), outStaticType);
    boolean dynamicTypeChanged =
        mutableJoinDynamicType(appView, inState.getDynamicType(), inStaticType, outStaticType);
    if (isEffectivelyUnknown()) {
      return unknown();
    }
    boolean inFlowChanged = mutableJoinInFlow(inState);
    if (widenInFlow(appView)) {
      return unknown();
    }
    boolean unusedChanged = mutableJoinUnused(inState);
    if (abstractValueChanged || dynamicTypeChanged || inFlowChanged || unusedChanged) {
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
      AppView<AppInfoWithLiveness> appView,
      DynamicType inDynamicType,
      DexType inStaticType,
      DexType outStaticType) {
    DynamicType oldDynamicType = dynamicType;
    DynamicType joinedDynamicType =
        dynamicType.join(appView, inDynamicType, inStaticType, outStaticType);
    DynamicType widenedDynamicType =
        WideningUtils.widenDynamicNonReceiverType(appView, joinedDynamicType, outStaticType);
    dynamicType = widenedDynamicType;
    return !dynamicType.equals(oldDynamicType);
  }

  public ConcreteClassTypeValueState withDynamicType(DynamicType dynamicType) {
    return new ConcreteClassTypeValueState(abstractValue, dynamicType, copyInFlow());
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof ConcreteClassTypeValueState)) {
      return false;
    }
    ConcreteClassTypeValueState state = (ConcreteClassTypeValueState) obj;
    return abstractValue.equals(state.abstractValue)
        && dynamicType.equals(state.dynamicType)
        && getInFlow().equals(state.getInFlow());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getClass(), abstractValue, dynamicType, getInFlow());
  }

  @Override
  public String toString() {
    assert !hasInFlow();
    return "ClassState(type: " + dynamicType + ", value: " + abstractValue + ")";
  }
}
