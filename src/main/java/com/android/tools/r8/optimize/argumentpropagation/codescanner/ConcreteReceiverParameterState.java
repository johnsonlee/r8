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
import com.android.tools.r8.optimize.argumentpropagation.utils.WideningUtils;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;
import java.util.Collections;
import java.util.Set;

public class ConcreteReceiverParameterState extends ConcreteReferenceTypeParameterState {

  private DynamicType dynamicType;

  public ConcreteReceiverParameterState(DynamicType dynamicType) {
    this(dynamicType, Collections.emptySet());
  }

  public ConcreteReceiverParameterState(DynamicType dynamicType, Set<InFlow> inFlow) {
    super(inFlow);
    this.dynamicType = dynamicType;
    assert !isEffectivelyBottom() : "Must use BottomReceiverParameterState instead";
    assert !isEffectivelyUnknown() : "Must use UnknownParameterState instead";
  }

  @Override
  public ParameterState clearInFlow() {
    if (hasInFlow()) {
      if (dynamicType.isBottom()) {
        return bottomReceiverParameter();
      }
      internalClearInFlow();
    }
    assert !isEffectivelyBottom();
    return this;
  }

  @Override
  public AbstractValue getAbstractValue(AppView<AppInfoWithLiveness> appView) {
    return AbstractValue.unknown();
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
    return ConcreteParameterStateKind.RECEIVER;
  }

  @Override
  public boolean isEffectivelyBottom() {
    return dynamicType.isBottom() && !hasInFlow();
  }

  @Override
  public boolean isEffectivelyUnknown() {
    return dynamicType.isUnknown();
  }

  @Override
  public boolean isReceiverParameter() {
    return true;
  }

  @Override
  public ConcreteReceiverParameterState asReceiverParameter() {
    return this;
  }

  @Override
  public ParameterState mutableCopy() {
    return new ConcreteReceiverParameterState(dynamicType, copyInFlow());
  }

  @Override
  public ParameterState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ConcreteReferenceTypeParameterState parameterState,
      DexType parameterType,
      Action onChangedAction) {
    // TODO(b/190154391): Always take in the static type as an argument, and unset the dynamic type
    //  if it equals the static type.
    assert parameterType == null || parameterType.isClassType();
    boolean dynamicTypeChanged =
        mutableJoinDynamicType(appView, parameterState.getDynamicType(), parameterType);
    if (isEffectivelyUnknown()) {
      return unknown();
    }
    boolean inFlowChanged = mutableJoinInFlow(parameterState);
    if (widenInFlow(appView)) {
      return unknown();
    }
    if (dynamicTypeChanged || inFlowChanged) {
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
    boolean dynamicTypeChanged =
        mutableJoinDynamicType(appView, fieldState.getDynamicType(), parameterType);
    if (isEffectivelyUnknown()) {
      return unknown();
    }
    boolean inFlowChanged = mutableJoinInFlow(fieldState.getInFlow());
    if (widenInFlow(appView)) {
      return unknown();
    }
    if (dynamicTypeChanged || inFlowChanged) {
      onChangedAction.execute();
    }
    return this;
  }

  private boolean mutableJoinDynamicType(
      AppView<AppInfoWithLiveness> appView, DynamicType otherDynamicType, DexType parameterType) {
    DynamicType oldDynamicType = dynamicType;
    DynamicType joinedDynamicType = dynamicType.join(appView, otherDynamicType);
    if (parameterType != null) {
      DynamicType widenedDynamicType =
          WideningUtils.widenDynamicNonReceiverType(appView, joinedDynamicType, parameterType);
      dynamicType = widenedDynamicType;
    } else {
      dynamicType = joinedDynamicType;
    }
    return !dynamicType.equals(oldDynamicType);
  }
}
