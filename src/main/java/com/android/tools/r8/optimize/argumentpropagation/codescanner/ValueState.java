// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.fieldaccess.state.ConcreteFieldState;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;

public abstract class ValueState {

  public static BottomValueState bottomArrayTypeParameter() {
    return BottomArrayTypeValueState.get();
  }

  public static BottomValueState bottomClassTypeParameter() {
    return BottomClassTypeValueState.get();
  }

  public static BottomValueState bottomPrimitiveTypeParameter() {
    return BottomPrimitiveTypeValueState.get();
  }

  public static BottomValueState bottomReceiverParameter() {
    return BottomReceiverValueState.get();
  }

  public static UnknownValueState unknown() {
    return UnknownValueState.get();
  }

  public abstract AbstractValue getAbstractValue(AppView<AppInfoWithLiveness> appView);

  public boolean isBottom() {
    return false;
  }

  public boolean isConcrete() {
    return false;
  }

  public ConcreteValueState asConcrete() {
    return null;
  }

  public NonEmptyValueState asNonEmpty() {
    return null;
  }

  public boolean isUnknown() {
    return false;
  }

  public abstract ValueState mutableCopy();

  public final ValueState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ValueState parameterState,
      DexType parameterType,
      StateCloner cloner) {
    return mutableJoin(appView, parameterState, parameterType, cloner, Action.empty());
  }

  public abstract ValueState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ValueState parameterState,
      DexType parameterType,
      StateCloner cloner,
      Action onChangedAction);

  public abstract ValueState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ConcreteFieldState fieldState,
      DexType parameterType,
      Action onChangedAction);
}
