// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.type.DynamicTypeWithUpperBound;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;
import java.util.Set;

public abstract class ConcreteReferenceTypeValueState extends ConcreteValueState {

  ConcreteReferenceTypeValueState(Set<InFlow> inFlow) {
    super(inFlow);
  }

  public abstract ValueState cast(AppView<AppInfoWithLiveness> appView, DexType type);

  protected static DynamicType cast(
      AppView<AppInfoWithLiveness> appView, DexType type, DynamicType dynamicType) {
    if (dynamicType.isBottom()) {
      return dynamicType;
    }
    if (dynamicType.isNotNullType() || dynamicType.isUnknown()) {
      Nullability nullability =
          dynamicType.isNotNullType() ? Nullability.definitelyNotNull() : Nullability.maybeNull();
      return DynamicType.create(appView, type.toTypeElement(appView, nullability));
    }
    assert dynamicType.isDynamicTypeWithUpperBound();
    DynamicTypeWithUpperBound dynamicTypeWithUpperBound = dynamicType.asDynamicTypeWithUpperBound();
    // If this is an upcast, then return the more precise type.
    TypeElement typeElement = type.toTypeElement(appView, dynamicType.getNullability());
    if (dynamicTypeWithUpperBound
        .getDynamicUpperBoundType()
        .lessThanOrEqual(typeElement, appView)) {
      return dynamicType;
    }
    // Otherwise this is a downcast.
    if (dynamicType.hasDynamicLowerBoundType()) {
      // There are three cases:
      // (1) the cast type is between the upper and lower bound,
      // (2) the cast type is below the lower bound, or
      // (3) the cast type is unrelated to the bounds.
      // In (2) and (3) the cast always fails unless the in-dynamic type can be null.
      ClassTypeElement lowerBound = dynamicTypeWithUpperBound.getDynamicLowerBoundType();
      if (typeElement.lessThanOrEqual(dynamicTypeWithUpperBound.getDynamicUpperBoundType(), appView)
          && lowerBound.lessThanOrEqual(typeElement, appView)) {
        return DynamicType.create(appView, typeElement, lowerBound);
      } else {
        return dynamicType.getNullability().isMaybeNull()
            ? DynamicType.definitelyNull()
            : DynamicType.bottom();
      }
    }
    return DynamicType.create(appView, typeElement);
  }

  public abstract DynamicType getDynamicType();

  public abstract Nullability getNullability();

  @Override
  public boolean isReferenceState() {
    return true;
  }

  @Override
  public ConcreteReferenceTypeValueState asReferenceState() {
    return this;
  }

  public abstract NonEmptyValueState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ConcreteReferenceTypeValueState inState,
      DexType inStaticType,
      DexType outStaticType,
      Action onChangedAction);
}
