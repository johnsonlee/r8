// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.compose;

import com.android.tools.r8.optimize.argumentpropagation.codescanner.AbstractFunction;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.BaseInFlow;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteValueState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.FlowGraphStateProvider;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.InFlow;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ValueState;
import com.android.tools.r8.utils.IterableUtils;
import java.util.Objects;

public class UpdateChangedFlagsAbstractFunction implements AbstractFunction {

  private final InFlow inFlow;

  public UpdateChangedFlagsAbstractFunction(InFlow inFlow) {
    this.inFlow = inFlow;
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
    assert inFlow.isBaseInFlow();
    return IterableUtils.singleton(inFlow.asBaseInFlow());
  }

  @Override
  public boolean isUpdateChangedFlagsAbstractFunction() {
    return true;
  }

  @Override
  public UpdateChangedFlagsAbstractFunction asUpdateChangedFlagsAbstractFunction() {
    return this;
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
    UpdateChangedFlagsAbstractFunction fn = (UpdateChangedFlagsAbstractFunction) obj;
    return inFlow.equals(fn.inFlow);
  }

  @Override
  public int hashCode() {
    return Objects.hash(getClass(), inFlow);
  }
}
