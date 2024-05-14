// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;

public class BottomReceiverValueState extends BottomValueState {

  private static final BottomReceiverValueState INSTANCE = new BottomReceiverValueState();

  private BottomReceiverValueState() {}

  public static BottomReceiverValueState get() {
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
    assert inState.isConcrete();
    assert inState.asConcrete().isReferenceState();
    ConcreteReferenceTypeValueState concreteState = inState.asConcrete().asReferenceState();
    if (concreteState.isReceiverState()) {
      return cloner.mutableCopy(concreteState);
    }
    DynamicType dynamicType = concreteState.getDynamicType();
    if (dynamicType.isUnknown()) {
      return unknown();
    }
    return new ConcreteReceiverValueState(dynamicType, concreteState.copyInFlow());
  }

  @Override
  public String toString() {
    return "⊥(RECEIVER)";
  }
}
