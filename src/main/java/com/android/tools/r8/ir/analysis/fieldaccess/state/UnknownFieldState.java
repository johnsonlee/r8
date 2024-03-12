// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldaccess.state;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.NonEmptyParameterState;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;
import java.util.function.Function;

/** Represents that nothing is known about the values that may flow into a given field. */
public class UnknownFieldState extends NonEmptyFieldState {

  private static final UnknownFieldState INSTANCE = new UnknownFieldState();

  private UnknownFieldState() {}

  public static UnknownFieldState getInstance() {
    return INSTANCE;
  }

  @Override
  public AbstractValue getAbstractValue() {
    return AbstractValue.unknown();
  }

  @Override
  public boolean isUnknown() {
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
    return this;
  }

  @Override
  public FieldState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ProgramField field,
      NonEmptyParameterState parameterState,
      Action onChangedAction) {
    return this;
  }

  @Override
  public FieldState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ProgramField field,
      Function<FieldState, NonEmptyFieldState> fieldStateSupplier) {
    return this;
  }
}
