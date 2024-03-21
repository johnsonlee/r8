// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import java.util.Objects;

/**
 * Encodes the `x | const` abstract function. This is currently used as part of the modeling of
 * updateChangedFlags, since the updateChangedFlags function is invoked with `changedFlags | 1` as
 * an argument.
 */
public class OrAbstractFunction implements AbstractFunction {

  public final InFlow inFlow;
  public final long constant;

  public OrAbstractFunction(InFlow inFlow, long constant) {
    this.inFlow = inFlow;
    this.constant = constant;
  }

  @Override
  public NonEmptyValueState apply(ConcreteValueState state) {
    // TODO(b/302483644): Implement this abstract function to allow correct value propagation of
    //  updateChangedFlags(x | 1).
    return state;
  }

  @Override
  public InFlow getBaseInFlow() {
    if (inFlow.isAbstractFunction()) {
      return inFlow.asAbstractFunction().getBaseInFlow();
    }
    assert inFlow.isFieldValue() || inFlow.isMethodParameter();
    return inFlow;
  }

  @Override
  @SuppressWarnings("EqualsGetClass")
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    OrAbstractFunction fn = (OrAbstractFunction) obj;
    return inFlow.equals(fn.inFlow) && constant == fn.constant;
  }

  @Override
  public int hashCode() {
    return Objects.hash(getClass(), inFlow, constant);
  }
}
