// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;
import com.android.tools.r8.utils.ObjectUtils;

public abstract class ValueState {

  public static BottomValueState bottom(ProgramField field) {
    return bottom(field.getType());
  }

  public static BottomValueState bottom(DexField field) {
    return bottom(field.getType());
  }

  public static BottomValueState bottom(DexType type) {
    if (type.isArrayType()) {
      return bottomArrayTypeState();
    } else if (type.isClassType()) {
      return bottomClassTypeState();
    } else {
      assert type.isPrimitiveType();
      return bottomPrimitiveTypeState();
    }
  }

  public static BottomArrayTypeValueState bottomArrayTypeState() {
    return BottomArrayTypeValueState.get();
  }

  public static BottomClassTypeValueState bottomClassTypeState() {
    return BottomClassTypeValueState.get();
  }

  public static BottomPrimitiveTypeValueState bottomPrimitiveTypeState() {
    return BottomPrimitiveTypeValueState.get();
  }

  public static BottomReceiverValueState bottomReceiverParameter() {
    return BottomReceiverValueState.get();
  }

  public static UnknownValueState unknown() {
    return UnknownValueState.get();
  }

  public static UnusedValueState unused(DexType type) {
    if (type.isArrayType()) {
      return unusedArrayTypeState();
    } else if (type.isClassType()) {
      return unusedClassTypeState();
    } else {
      assert type.isPrimitiveType();
      return unusedPrimitiveTypeState();
    }
  }

  public static UnusedArrayTypeValueState unusedArrayTypeState() {
    return UnusedArrayTypeValueState.get();
  }

  public static UnusedClassTypeValueState unusedClassTypeState() {
    return UnusedClassTypeValueState.get();
  }

  public static UnusedPrimitiveTypeValueState unusedPrimitiveTypeState() {
    return UnusedPrimitiveTypeValueState.get();
  }

  public abstract AbstractValue getAbstractValue(AppView<AppInfoWithLiveness> appView);

  public boolean isArrayState() {
    return false;
  }

  public ConcreteArrayTypeValueState asArrayState() {
    return null;
  }

  public boolean isBottom() {
    return false;
  }

  public boolean isClassState() {
    return false;
  }

  public ConcreteClassTypeValueState asClassState() {
    return null;
  }

  public boolean isConcrete() {
    return false;
  }

  public ConcreteValueState asConcrete() {
    return null;
  }

  public boolean isNonEmpty() {
    return false;
  }

  public NonEmptyValueState asNonEmpty() {
    return null;
  }

  public boolean isPrimitiveState() {
    return false;
  }

  public ConcretePrimitiveTypeValueState asPrimitiveState() {
    return null;
  }

  public boolean isReceiverState() {
    return false;
  }

  public ConcreteReceiverValueState asReceiverState() {
    return null;
  }

  public boolean isReferenceState() {
    return false;
  }

  public ConcreteReferenceTypeValueState asReferenceState() {
    return null;
  }

  public boolean isUnknown() {
    return false;
  }

  public boolean isUnused() {
    return false;
  }

  public abstract ValueState mutableCopy();

  public abstract ValueState mutableCopyWithoutInFlow();

  public final ValueState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ValueState inState,
      DexType inStaticType,
      DexType outStaticType,
      StateCloner cloner) {
    return mutableJoin(appView, inState, inStaticType, outStaticType, cloner, Action.empty());
  }

  public abstract ValueState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ValueState inState,
      DexType inStaticType,
      DexType outStaticType,
      StateCloner cloner,
      Action onChangedAction);

  @Override
  public abstract boolean equals(Object obj);

  @Override
  public abstract int hashCode();

  public final boolean identical(ValueState state) {
    return ObjectUtils.identical(this, state);
  }
}
