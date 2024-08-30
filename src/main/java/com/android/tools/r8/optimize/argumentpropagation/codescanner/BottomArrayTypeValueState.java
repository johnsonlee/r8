// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;

public class BottomArrayTypeValueState extends BottomValueState {

  private static final BottomArrayTypeValueState INSTANCE = new BottomArrayTypeValueState();

  private BottomArrayTypeValueState() {}

  public static BottomArrayTypeValueState get() {
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
      assert inState.identical(unusedArrayTypeState());
      return inState;
    }
    assert inState.isConcrete();
    assert inState.asConcrete().isReferenceState();
    ConcreteReferenceTypeValueState concreteState = inState.asConcrete().asReferenceState();
    if (concreteState.isArrayState()) {
      return cloner.mutableCopy(concreteState);
    }
    Nullability nullability = concreteState.getNullability();
    if (nullability.isMaybeNull()) {
      return unknown();
    }
    return new ConcreteArrayTypeValueState(nullability, concreteState.copyInFlow());
  }

  @Override
  public String toString() {
    return "⊥(ARRAY)";
  }
}
