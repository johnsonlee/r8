// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.fieldaccess.state.ConcreteReferenceTypeFieldState;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;
import java.util.Set;

public abstract class ConcreteReferenceTypeValueState extends ConcreteValueState {

  ConcreteReferenceTypeValueState(Set<InFlow> inFlow) {
    super(inFlow);
  }

  public abstract DynamicType getDynamicType();

  public abstract Nullability getNullability();

  @Override
  public boolean isReferenceParameter() {
    return true;
  }

  @Override
  public ConcreteReferenceTypeValueState asReferenceParameter() {
    return this;
  }

  public abstract ValueState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ConcreteReferenceTypeValueState parameterState,
      DexType parameterType,
      Action onChangedAction);

  public abstract ValueState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ConcreteReferenceTypeFieldState fieldState,
      DexType parameterType,
      Action onChangedAction);
}
