// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.fieldaccess.state.ConcreteReferenceTypeFieldState;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;
import com.android.tools.r8.utils.SetUtils;
import java.util.Collections;
import java.util.Set;

public class ConcreteArrayTypeParameterState extends ConcreteReferenceTypeParameterState {

  private Nullability nullability;

  public ConcreteArrayTypeParameterState(InFlow inFlow) {
    this(Nullability.bottom(), SetUtils.newHashSet(inFlow));
  }

  public ConcreteArrayTypeParameterState(Nullability nullability) {
    this(nullability, Collections.emptySet());
  }

  public ConcreteArrayTypeParameterState(Nullability nullability, Set<InFlow> inFlow) {
    super(inFlow);
    this.nullability = nullability;
    assert !isEffectivelyBottom() : "Must use BottomArrayTypeParameterState instead";
    assert !isEffectivelyUnknown() : "Must use UnknownParameterState instead";
  }

  @Override
  public ParameterState clearInFlow() {
    if (hasInFlow()) {
      if (nullability.isBottom()) {
        return bottomArrayTypeParameter();
      }
      internalClearInFlow();
    }
    assert !isEffectivelyBottom();
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
  public ConcreteParameterStateKind getKind() {
    return ConcreteParameterStateKind.ARRAY;
  }

  @Override
  public Nullability getNullability() {
    return nullability;
  }

  @Override
  public boolean isArrayParameter() {
    return true;
  }

  @Override
  public ConcreteArrayTypeParameterState asArrayParameter() {
    return this;
  }

  @Override
  public boolean isEffectivelyBottom() {
    return nullability.isBottom() && !hasInFlow();
  }

  @Override
  public boolean isEffectivelyUnknown() {
    return nullability.isUnknown();
  }

  @Override
  public ParameterState mutableCopy() {
    return new ConcreteArrayTypeParameterState(nullability, copyInFlow());
  }

  @Override
  public ParameterState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ConcreteReferenceTypeParameterState parameterState,
      DexType parameterType,
      Action onChangedAction) {
    assert parameterType.isArrayType();
    assert !nullability.isUnknown();
    boolean nullabilityChanged = mutableJoinNullability(parameterState.getNullability());
    if (isEffectivelyUnknown()) {
      return unknown();
    }
    boolean inFlowChanged = mutableJoinInFlow(parameterState);
    if (widenInFlow(appView)) {
      return unknown();
    }
    if (nullabilityChanged || inFlowChanged) {
      onChangedAction.execute();
    }
    return this;
  }

  @Override
  public ParameterState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ConcreteReferenceTypeFieldState fieldState,
      DexType parameterType,
      Action onChangedAction) {
    assert fieldState.isArray();
    // We do not track the nullability of array fields.
    return unknown();
  }

  private boolean mutableJoinNullability(Nullability otherNullability) {
    Nullability oldNullability = nullability;
    nullability = nullability.join(otherNullability);
    return nullability != oldNullability;
  }
}
