// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.fieldaccess.state;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.AbstractValueFactory;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.function.Function;

public abstract class NonEmptyFieldState extends FieldState {

  @Override
  public NonEmptyFieldState asNonEmpty() {
    return this;
  }

  public abstract AbstractValue getAbstractValue();

  @Override
  public final AbstractValue getAbstractValue(
      AbstractValueFactory abstractValueFactory, ProgramField field) {
    return getAbstractValue();
  }

  public abstract FieldState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ProgramField field,
      Function<FieldState, NonEmptyFieldState> fieldStateSupplier);
}
