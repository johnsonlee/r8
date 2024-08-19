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
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;
import com.android.tools.r8.utils.SetUtils;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

public class ConcreteArrayTypeValueState extends ConcreteReferenceTypeValueState {

  private Nullability nullability;

  public ConcreteArrayTypeValueState(InFlow inFlow) {
    this(Nullability.bottom(), SetUtils.newHashSet(inFlow));
  }

  public ConcreteArrayTypeValueState(Nullability nullability) {
    this(nullability, Collections.emptySet());
  }

  public ConcreteArrayTypeValueState(Nullability nullability, Set<InFlow> inFlow) {
    super(inFlow);
    this.nullability = nullability;
    assert !isEffectivelyBottom() : "Must use BottomArrayTypeParameterState instead";
    assert !isEffectivelyUnknown() : "Must use UnknownParameterState instead";
  }

  public static NonEmptyValueState create(Nullability nullability) {
    return nullability.isUnknown() ? unknown() : new ConcreteArrayTypeValueState(nullability);
  }

  @Override
  public ValueState cast(AppView<AppInfoWithLiveness> appView, DexType type) {
    return this;
  }

  @Override
  public AbstractValue getAbstractValue(AppView<AppInfoWithLiveness> appView) {
    if (getNullability().isDefinitelyNull()) {
      return appView.abstractValueFactory().createUncheckedNullValue();
    }
    return AbstractValue.unknown();
  }

  @Override
  public DynamicType getDynamicType() {
    return DynamicType.unknown();
  }

  @Override
  public BottomValueState getCorrespondingBottom() {
    return bottomArrayTypeState();
  }

  @Override
  public ConcreteParameterStateKind getKind() {
    return ConcreteParameterStateKind.ARRAY;
  }

  @Override
  public Nullability getNullability() {
    return nullability;
  }

  @Override
  public boolean isArrayState() {
    return true;
  }

  @Override
  public ConcreteArrayTypeValueState asArrayState() {
    return this;
  }

  @Override
  public boolean isEffectivelyBottomIgnoringInFlow() {
    return nullability.isBottom();
  }

  @Override
  public boolean isEffectivelyUnknown() {
    return nullability.isUnknown();
  }

  @Override
  public ConcreteArrayTypeValueState internalMutableCopy(Supplier<Set<InFlow>> inFlowSupplier) {
    return new ConcreteArrayTypeValueState(nullability, inFlowSupplier.get());
  }

  public NonEmptyValueState mutableJoin(
      AppView<AppInfoWithLiveness> appView, ProgramField field, Nullability nullability) {
    mutableJoinNullability(nullability);
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
    assert outStaticType.isArrayType();
    boolean nullabilityChanged = mutableJoinNullability(inState.getNullability());
    if (isEffectivelyUnknown()) {
      return unknown();
    }
    boolean inFlowChanged = mutableJoinInFlow(inState);
    if (widenInFlow(appView)) {
      return unknown();
    }
    if (nullabilityChanged || inFlowChanged) {
      onChangedAction.execute();
    }
    return this;
  }

  private boolean mutableJoinNullability(Nullability otherNullability) {
    Nullability oldNullability = nullability;
    nullability = nullability.join(otherNullability);
    return nullability != oldNullability;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof ConcreteArrayTypeValueState)) {
      return false;
    }
    ConcreteArrayTypeValueState state = (ConcreteArrayTypeValueState) obj;
    return nullability.equals(state.nullability) && getInFlow().equals(state.getInFlow());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getClass(), nullability, getInFlow());
  }

  @Override
  public String toString() {
    assert !hasInFlow();
    return "ArrayState(" + nullability + ")";
  }
}
