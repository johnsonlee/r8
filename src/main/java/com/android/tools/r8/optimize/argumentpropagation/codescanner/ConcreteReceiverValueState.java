// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.optimize.argumentpropagation.utils.WideningUtils;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;
import java.util.Collections;
import java.util.Set;

public class ConcreteReceiverValueState extends ConcreteReferenceTypeValueState {

  private DynamicType dynamicType;

  public ConcreteReceiverValueState(DynamicType dynamicType) {
    this(dynamicType, Collections.emptySet());
  }

  public ConcreteReceiverValueState(DynamicType dynamicType, Set<InFlow> inFlow) {
    super(inFlow);
    this.dynamicType = dynamicType;
    assert !isEffectivelyBottom() : "Must use BottomReceiverParameterState instead";
    assert !isEffectivelyUnknown() : "Must use UnknownParameterState instead";
  }

  @Override
  public AbstractValue getAbstractValue(AppView<AppInfoWithLiveness> appView) {
    return AbstractValue.unknown();
  }

  @Override
  public BottomValueState getCorrespondingBottom() {
    return bottomReceiverParameter();
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
  public boolean isReceiverState() {
    return true;
  }

  @Override
  public ConcreteReceiverValueState asReceiverState() {
    return this;
  }

  @Override
  public ValueState mutableCopy() {
    return new ConcreteReceiverValueState(dynamicType, copyInFlow());
  }

  @Override
  public NonEmptyValueState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ConcreteReferenceTypeValueState state,
      DexType staticType,
      Action onChangedAction) {
    // TODO(b/190154391): Always take in the static type as an argument, and unset the dynamic type
    //  if it equals the static type.
    assert staticType == null || staticType.isClassType();
    boolean dynamicTypeChanged =
        mutableJoinDynamicType(appView, state.getDynamicType(), staticType);
    if (isEffectivelyUnknown()) {
      return unknown();
    }
    boolean inFlowChanged = mutableJoinInFlow(state);
    if (widenInFlow(appView)) {
      return unknown();
    }
    if (dynamicTypeChanged || inFlowChanged) {
      onChangedAction.execute();
    }
    return this;
  }

  private boolean mutableJoinDynamicType(
      AppView<AppInfoWithLiveness> appView, DynamicType otherDynamicType, DexType staticType) {
    DynamicType oldDynamicType = dynamicType;
    DynamicType joinedDynamicType = dynamicType.join(appView, otherDynamicType);
    if (staticType != null) {
      DynamicType widenedDynamicType =
          WideningUtils.widenDynamicNonReceiverType(appView, joinedDynamicType, staticType);
      dynamicType = widenedDynamicType;
    } else {
      dynamicType = joinedDynamicType;
    }
    return !dynamicType.equals(oldDynamicType);
  }
}
