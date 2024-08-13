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
import java.util.function.Supplier;

public class ConcreteReceiverValueState extends ConcreteReferenceTypeValueState {

  private DynamicType dynamicType;

  public ConcreteReceiverValueState(DynamicType dynamicType) {
    this(dynamicType, Collections.emptySet());
  }

  public ConcreteReceiverValueState(DynamicType dynamicType, Set<InFlow> inFlow) {
    super(inFlow);
    assert !dynamicType.isUnknown();
    this.dynamicType = dynamicType;
    assert !isEffectivelyBottom() : "Must use BottomReceiverParameterState instead";
    assert !isEffectivelyUnknown() : "Must use UnknownParameterState instead";
  }

  @Override
  public ValueState cast(AppView<AppInfoWithLiveness> appView, DexType type) {
    DynamicType castDynamicType = cast(appView, type, dynamicType);
    if (castDynamicType.equals(dynamicType)) {
      return this;
    }
    if (castDynamicType.isBottom()) {
      return bottomReceiverParameter();
    }
    assert castDynamicType.isDynamicTypeWithUpperBound();
    return new ConcreteReceiverValueState(castDynamicType, copyInFlow());
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
    assert !dynamicType.isUnknown();
    return dynamicType;
  }

  @Override
  public Nullability getNullability() {
    return Nullability.definitelyNotNull();
  }

  @Override
  public ConcreteParameterStateKind getKind() {
    return ConcreteParameterStateKind.RECEIVER;
  }

  @Override
  public boolean isEffectivelyBottomIgnoringInFlow() {
    return dynamicType.isBottom();
  }

  @Override
  public boolean isEffectivelyUnknown() {
    assert !dynamicType.isUnknown();
    return dynamicType.isNotNullType();
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
  public ConcreteReceiverValueState internalMutableCopy(Supplier<Set<InFlow>> inFlowSupplier) {
    return new ConcreteReceiverValueState(dynamicType, inFlowSupplier.get());
  }

  @Override
  public NonEmptyValueState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ConcreteReferenceTypeValueState inState,
      DexType inStaticType,
      DexType outStaticType,
      Action onChangedAction) {
    // TODO(b/190154391): Always take in the static type as an argument, and unset the dynamic type
    //  if it equals the static type.
    assert outStaticType == null || outStaticType.isClassType();
    boolean dynamicTypeChanged =
        mutableJoinDynamicType(appView, inState.getDynamicType(), inStaticType, outStaticType);
    if (isEffectivelyUnknown()) {
      return unknown();
    }
    boolean inFlowChanged = mutableJoinInFlow(inState);
    if (widenInFlow(appView)) {
      return unknown();
    }
    if (dynamicTypeChanged || inFlowChanged) {
      onChangedAction.execute();
    }
    return this;
  }

  private boolean mutableJoinDynamicType(
      AppView<AppInfoWithLiveness> appView,
      DynamicType inDynamicType,
      DexType inStaticType,
      DexType outStaticType) {
    DynamicType oldDynamicType = dynamicType;
    DynamicType joinedDynamicType =
        dynamicType.join(appView, inDynamicType, inStaticType, outStaticType);
    if (outStaticType != null) {
      DynamicType widenedDynamicType =
          WideningUtils.widenDynamicNonReceiverType(appView, joinedDynamicType, outStaticType);
      assert !widenedDynamicType.isUnknown();
      dynamicType = widenedDynamicType;
    } else {
      assert !joinedDynamicType.isUnknown();
      dynamicType = joinedDynamicType;
    }
    return !dynamicType.equals(oldDynamicType);
  }

  public ConcreteReceiverValueState withDynamicType(DynamicType dynamicType) {
    return new ConcreteReceiverValueState(dynamicType, copyInFlow());
  }

  @Override
  public String toString() {
    assert !hasInFlow();
    return "ReceiverState(" + dynamicType + ")";
  }
}
