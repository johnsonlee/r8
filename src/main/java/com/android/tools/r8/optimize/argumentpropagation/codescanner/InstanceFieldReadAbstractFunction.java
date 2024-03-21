// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.ir.analysis.value.AbstractValue;

public class InstanceFieldReadAbstractFunction implements AbstractFunction {

  private final BaseInFlow receiver;
  private final DexField field;

  public InstanceFieldReadAbstractFunction(BaseInFlow receiver, DexField field) {
    this.receiver = receiver;
    this.field = field;
  }

  // TODO(b/296030319): Instead of returning unknown from here, we should fallback to the state of
  //  the instance field node in the graph. A prerequisite for this is the ability to express
  //  multiple inputs to abstract functions.
  @Override
  public NonEmptyValueState apply(ConcreteValueState state) {
    if (!state.isClassState()) {
      return ValueState.unknown();
    }
    ConcreteClassTypeValueState classState = state.asClassState();
    if (classState.getNullability().isDefinitelyNull()) {
      // TODO(b/296030319): This should be rare, but we should really return bottom here, since
      //  reading a field from the the null value throws an exception, meaning no flow should be
      //  propagated.
      return ValueState.unknown();
    }
    AbstractValue abstractValue = state.getAbstractValue(null);
    if (!abstractValue.hasObjectState()) {
      return ValueState.unknown();
    }
    AbstractValue fieldValue = abstractValue.getObjectState().getAbstractFieldValue(field);
    if (fieldValue.isUnknown()) {
      return ValueState.unknown();
    }
    return ConcreteValueState.create(field.getType(), fieldValue);
  }

  @Override
  public InFlow getBaseInFlow() {
    return receiver;
  }

  @Override
  public boolean isInstanceFieldReadAbstractFunction() {
    return true;
  }

  @Override
  public InstanceFieldReadAbstractFunction asInstanceFieldReadAbstractFunction() {
    return this;
  }
}
