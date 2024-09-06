// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;

public class UnusedArrayTypeValueState extends UnusedValueState {

  private static final UnusedArrayTypeValueState INSTANCE = new UnusedArrayTypeValueState();

  private UnusedArrayTypeValueState() {}

  public static UnusedArrayTypeValueState get() {
    return INSTANCE;
  }

  @Override
  public NonEmptyValueState mutableJoin(
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
      return unknown();
    }
    assert inState.isConcrete();
    assert inState.asConcrete().isReferenceState();
    NonEmptyValueState result =
        bottomArrayTypeState()
            .mutableJoin(appView, inState, inStaticType, outStaticType, cloner, onChangedAction)
            .asNonEmpty();
    if (result.isConcrete()) {
      return result.asConcrete().mutableJoinUnused(this);
    } else {
      assert result.isUnknown();
      return result;
    }
  }

  @Override
  public String toString() {
    return "UNUSED(ARRAY)";
  }
}
