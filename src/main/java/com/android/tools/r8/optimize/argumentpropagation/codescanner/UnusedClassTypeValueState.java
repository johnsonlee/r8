// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;

public class UnusedClassTypeValueState extends UnusedValueState {

  private static final UnusedClassTypeValueState INSTANCE = new UnusedClassTypeValueState();

  private UnusedClassTypeValueState() {}

  public static UnusedClassTypeValueState get() {
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
    if (inState.isBottom() || inState.isUnused()) {
      return this;
    }
    if (inState.isUnknown()) {
      return inState;
    }
    assert inState.isConcrete();
    assert inState.asConcrete().isReferenceState();
    return ValueState.bottomClassTypeState()
        .mutableJoin(appView, inState, inStaticType, outStaticType, cloner, onChangedAction);
  }

  @Override
  public String toString() {
    return "UNUSED(CLASS)";
  }
}
