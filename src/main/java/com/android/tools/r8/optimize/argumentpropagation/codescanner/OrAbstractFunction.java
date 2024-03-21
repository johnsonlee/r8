// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.utils.IterableUtils;
import java.util.Objects;

/**
 * Encodes the `x | const` abstract function. This is currently used as part of the modeling of
 * updateChangedFlags, since the updateChangedFlags function is invoked with `changedFlags | 1` as
 * an argument.
 */
public class OrAbstractFunction implements AbstractFunction {

  public final BaseInFlow inFlow;
  public final long constant;

  public OrAbstractFunction(BaseInFlow inFlow, long constant) {
    this.inFlow = inFlow;
    this.constant = constant;
  }

  @Override
  public ValueState apply(FlowGraphStateProvider flowGraphStateProvider, ConcreteValueState state) {
    // TODO(b/302483644): Implement this abstract function to allow correct value propagation of
    //  updateChangedFlags(x | 1).
    return state;
  }

  @Override
  public boolean containsBaseInFlow(BaseInFlow otherInFlow) {
    if (inFlow.isAbstractFunction()) {
      return inFlow.asAbstractFunction().containsBaseInFlow(otherInFlow);
    }
    assert inFlow.isBaseInFlow();
    return inFlow.equals(otherInFlow);
  }

  @Override
  public Iterable<BaseInFlow> getBaseInFlow() {
    if (inFlow.isAbstractFunction()) {
      return inFlow.asAbstractFunction().getBaseInFlow();
    }
    return IterableUtils.singleton(inFlow);
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
