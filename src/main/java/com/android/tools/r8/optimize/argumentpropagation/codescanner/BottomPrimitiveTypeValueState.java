// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;

public class BottomPrimitiveTypeValueState extends BottomValueState {

  private static final BottomPrimitiveTypeValueState INSTANCE = new BottomPrimitiveTypeValueState();

  private BottomPrimitiveTypeValueState() {}

  public static BottomPrimitiveTypeValueState get() {
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
      assert inState.identical(bottomPrimitiveTypeState());
      return this;
    }
    if (inState.isUnknown()) {
      return inState;
    }
    assert inState.isPrimitiveState();
    return cloner.mutableCopy(inState);
  }

  @Override
  public String toString() {
    return "⊥(PRIMITIVE)";
  }
}
