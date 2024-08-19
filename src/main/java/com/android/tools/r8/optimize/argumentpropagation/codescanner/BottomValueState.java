// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public abstract class BottomValueState extends ValueState {

  BottomValueState() {}

  @Override
  public final AbstractValue getAbstractValue(AppView<AppInfoWithLiveness> appView) {
    return AbstractValue.bottom();
  }

  @Override
  public final boolean isBottom() {
    return true;
  }

  @Override
  public final ValueState mutableCopy() {
    return this;
  }

  @Override
  public ValueState mutableCopyWithoutInFlow() {
    return this;
  }

  @Override
  public final boolean equals(Object obj) {
    return this == obj;
  }

  @Override
  public final int hashCode() {
    return System.identityHashCode(this);
  }
}
