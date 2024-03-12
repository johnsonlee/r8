// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;
import java.util.function.Function;

public class UnknownValueState extends NonEmptyValueState {

  private static final UnknownValueState INSTANCE = new UnknownValueState();

  private UnknownValueState() {}

  public static UnknownValueState get() {
    return INSTANCE;
  }

  @Override
  public AbstractValue getAbstractValue(AppView<AppInfoWithLiveness> appView) {
    return AbstractValue.unknown();
  }

  @Override
  public boolean isUnknown() {
    return true;
  }

  @Override
  public ValueState mutableCopy() {
    return this;
  }

  @Override
  public ValueState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ValueState parameterState,
      DexType parameterType,
      StateCloner cloner,
      Action onChangedAction) {
    return this;
  }

  @Override
  public NonEmptyValueState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      Function<ValueState, NonEmptyValueState> stateSupplier,
      DexType staticType,
      StateCloner cloner,
      Action onChangedAction) {
    return this;
  }
}
