// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldaccess.state;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteReferenceTypeValueState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.InFlow;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;
import java.util.Set;

/** The information that we track for fields whose type is a reference type. */
public abstract class ConcreteReferenceTypeFieldState extends ConcreteFieldState {

  ConcreteReferenceTypeFieldState(AbstractValue abstractValue, Set<InFlow> inFlow) {
    super(abstractValue, inFlow);
  }

  // TODO(b/296030319): Consider leveraging the static field type here. This type may be more
  //  precise than that of a parameter, for example, unlike the unknown type.
  public DynamicType getDynamicType() {
    return DynamicType.unknown();
  }

  @Override
  public boolean isReference() {
    return true;
  }

  @Override
  public ConcreteReferenceTypeFieldState asReference() {
    return this;
  }

  public abstract FieldState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ProgramField field,
      ConcreteReferenceTypeFieldState fieldState,
      Action onChangedAction);

  public abstract FieldState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ProgramField field,
      ConcreteReferenceTypeValueState parameterState,
      Action onChangedAction);
}
