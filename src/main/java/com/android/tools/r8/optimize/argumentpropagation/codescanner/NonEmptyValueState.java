// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;

public abstract class NonEmptyValueState extends ValueState {

  @Override
  public boolean isNonEmpty() {
    return true;
  }

  @Override
  public NonEmptyValueState asNonEmpty() {
    return this;
  }

  @Override
  public NonEmptyValueState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ValueState inState,
      DexType inStaticType,
      DexType outStaticType,
      StateCloner cloner) {
    return mutableJoin(appView, inState, inStaticType, outStaticType, cloner, Action.empty());
  }

  @Override
  public abstract NonEmptyValueState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ValueState inState,
      DexType inStaticType,
      DexType outStaticType,
      StateCloner cloner,
      Action onChangedAction);
}
