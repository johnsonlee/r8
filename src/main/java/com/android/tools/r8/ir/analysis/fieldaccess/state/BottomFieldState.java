// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldaccess.state;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.AbstractValueFactory;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteParameterState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.NonEmptyParameterState;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;

/** Used to represent the state for fields that have never been assigned in the program. */
public class BottomFieldState extends FieldState {

  private static final BottomFieldState INSTANCE = new BottomFieldState();

  private BottomFieldState() {}

  public static BottomFieldState getInstance() {
    return INSTANCE;
  }

  @Override
  public AbstractValue getAbstractValue(
      AbstractValueFactory abstractValueFactory, ProgramField field) {
    return abstractValueFactory.createDefaultValue(field.getType());
  }

  @Override
  public boolean isBottom() {
    return true;
  }

  @Override
  public FieldState mutableCopy() {
    return this;
  }

  @Override
  public FieldState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ProgramField field,
      NonEmptyFieldState fieldState,
      Action onChangedAction) {
    return fieldState.mutableCopy();
  }

  @Override
  public FieldState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ProgramField field,
      NonEmptyParameterState parameterState,
      Action onChangedAction) {
    if (parameterState.isUnknown()) {
      return unknown();
    }
    ConcreteParameterState concreteParameterState = parameterState.asConcrete();
    if (field.getType().isArrayType()) {
      return ConcreteArrayTypeFieldState.create(
          concreteParameterState.getAbstractValue(appView), concreteParameterState.copyInFlow());
    } else if (field.getType().isReferenceType()) {
      return ConcreteClassTypeFieldState.create(
          concreteParameterState.getAbstractValue(appView),
          concreteParameterState.asReferenceParameter().getDynamicType(),
          concreteParameterState.copyInFlow());
    } else {
      assert field.getType().isPrimitiveType();
      return ConcretePrimitiveTypeFieldState.create(
          concreteParameterState.getAbstractValue(appView), concreteParameterState.copyInFlow());
    }
  }
}
