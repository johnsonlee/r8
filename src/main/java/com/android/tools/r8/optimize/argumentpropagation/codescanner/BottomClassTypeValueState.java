// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.fieldaccess.state.ConcreteFieldState;
import com.android.tools.r8.ir.analysis.fieldaccess.state.ConcreteReferenceTypeFieldState;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.optimize.argumentpropagation.utils.WideningUtils;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;

public class BottomClassTypeValueState extends BottomValueState {

  private static final BottomClassTypeValueState INSTANCE = new BottomClassTypeValueState();

  private BottomClassTypeValueState() {}

  public static BottomClassTypeValueState get() {
    return INSTANCE;
  }

  @Override
  public ValueState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ValueState parameterState,
      DexType parameterType,
      StateCloner cloner,
      Action onChangedAction) {
    if (parameterState.isBottom()) {
      return this;
    }
    if (parameterState.isUnknown()) {
      return parameterState;
    }
    assert parameterState.isConcrete();
    assert parameterState.asConcrete().isReferenceParameter();
    ConcreteReferenceTypeValueState concreteParameterState =
        parameterState.asConcrete().asReferenceParameter();
    AbstractValue abstractValue = concreteParameterState.getAbstractValue(appView);
    DynamicType dynamicType = concreteParameterState.getDynamicType();
    DynamicType widenedDynamicType =
        WideningUtils.widenDynamicNonReceiverType(appView, dynamicType, parameterType);
    if (concreteParameterState.isClassParameter() && !widenedDynamicType.isUnknown()) {
      return cloner.mutableCopy(concreteParameterState);
    }
    return abstractValue.isUnknown() && widenedDynamicType.isUnknown()
        ? unknown()
        : new ConcreteClassTypeValueState(
            abstractValue, widenedDynamicType, concreteParameterState.copyInFlow());
  }

  @Override
  public ValueState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ConcreteFieldState fieldState,
      DexType parameterType,
      Action onChangedAction) {
    ConcreteReferenceTypeFieldState referenceFieldState = fieldState.asReference();
    AbstractValue abstractValue = referenceFieldState.getAbstractValue();
    DynamicType dynamicType = referenceFieldState.getDynamicType();
    return new ConcreteClassTypeValueState(
        abstractValue, dynamicType, referenceFieldState.copyInFlow());
  }
}
