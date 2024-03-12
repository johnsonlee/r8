// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.fieldaccess.state.ConcretePrimitiveTypeFieldState;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;
import com.android.tools.r8.utils.SetUtils;
import java.util.Collections;
import java.util.Set;

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
    this(AbstractValue.bottom(), SetUtils.newHashSet(inFlow));
  }

  @Override
  public ValueState clearInFlow() {
    if (hasInFlow()) {
      if (abstractValue.isBottom()) {
        return bottomPrimitiveTypeParameter();
      }
      internalClearInFlow();
    }
    assert !isEffectivelyBottom();
    return this;
  }

  @Override
  public ValueState mutableCopy() {
    return new ConcretePrimitiveTypeValueState(abstractValue, copyInFlow());
  }

  public ValueState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ConcretePrimitiveTypeValueState parameterState,
      DexType parameterType,
      Action onChangedAction) {
    assert parameterType.isPrimitiveType();
    boolean abstractValueChanged =
        mutableJoinAbstractValue(appView, parameterState.getAbstractValue(), parameterType);
    if (isEffectivelyUnknown()) {
      return unknown();
    }
    boolean inFlowChanged = mutableJoinInFlow(parameterState);
    if (widenInFlow(appView)) {
      return unknown();
    }
    if (abstractValueChanged || inFlowChanged) {
      onChangedAction.execute();
    }
    return this;
  }

  public ValueState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ConcretePrimitiveTypeFieldState fieldState,
      DexType parameterType,
      Action onChangedAction) {
    assert parameterType.isPrimitiveType();
    boolean abstractValueChanged =
        mutableJoinAbstractValue(appView, fieldState.getAbstractValue(), parameterType);
    if (isEffectivelyUnknown()) {
      return unknown();
    }
    boolean inFlowChanged = mutableJoinInFlow(fieldState.getInFlow());
    if (widenInFlow(appView)) {
      return unknown();
    }
    if (abstractValueChanged || inFlowChanged) {
      onChangedAction.execute();
    }
    return this;
  }

  private boolean mutableJoinAbstractValue(
      AppView<AppInfoWithLiveness> appView,
      AbstractValue otherAbstractValue,
      DexType parameterType) {
    AbstractValue oldAbstractValue = abstractValue;
    abstractValue =
        appView
            .getAbstractValueParameterJoiner()
            .join(abstractValue, otherAbstractValue, parameterType);
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
  public ConcreteParameterStateKind getKind() {
    return ConcreteParameterStateKind.PRIMITIVE;
  }

  @Override
  public boolean isEffectivelyBottom() {
    return abstractValue.isBottom() && !hasInFlow();
  }

  @Override
  public boolean isEffectivelyUnknown() {
    return abstractValue.isUnknown();
  }

  @Override
  public boolean isPrimitiveParameter() {
    return true;
  }

  @Override
  public ConcretePrimitiveTypeValueState asPrimitiveParameter() {
    return this;
  }
}
