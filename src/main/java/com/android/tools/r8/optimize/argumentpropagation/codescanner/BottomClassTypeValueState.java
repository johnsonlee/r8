// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
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
      ValueState inState,
      DexType inStaticType,
      DexType outStaticType,
      StateCloner cloner,
      Action onChangedAction) {
    if (inState.isBottom()) {
      return this;
    }
    if (inState.isUnknown()) {
      return inState;
    }
    if (inState.isUnused()) {
      assert inState.identical(unusedClassTypeState());
      return inState;
    }
    assert inState.isConcrete();
    assert inState.asConcrete().isReferenceState();
    ConcreteReferenceTypeValueState concreteState = inState.asConcrete().asReferenceState();
    DynamicType joinedDynamicType =
        joinDynamicType(appView, concreteState.getDynamicType(), inStaticType, outStaticType);
    if (concreteState.isClassState() && concreteState.getDynamicType().equals(joinedDynamicType)) {
      return cloner.mutableCopy(concreteState);
    }
    AbstractValue abstractValue = concreteState.getAbstractValue(appView);
    return ConcreteClassTypeValueState.create(
        abstractValue, joinedDynamicType, concreteState.copyInFlow());
  }

  private DynamicType joinDynamicType(
      AppView<AppInfoWithLiveness> appView,
      DynamicType inDynamicType,
      DexType inStaticType,
      DexType outStaticType) {
    DynamicType oldDynamicType = DynamicType.bottom();
    DynamicType joinedDynamicType =
        oldDynamicType.join(appView, inDynamicType, inStaticType, outStaticType);
    if (outStaticType != null) {
      return WideningUtils.widenDynamicNonReceiverType(appView, joinedDynamicType, outStaticType);
    } else {
      return joinedDynamicType;
    }
  }

  @Override
  public String toString() {
    return "⊥(CLASS)";
  }
}
