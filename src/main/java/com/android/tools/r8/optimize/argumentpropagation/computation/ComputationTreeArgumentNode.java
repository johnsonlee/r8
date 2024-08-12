// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.computation;

import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.AbstractValueFactory;
import com.android.tools.r8.utils.ArrayUtils;
import java.util.Objects;
import java.util.function.IntFunction;

/** Represents the read of an argument. */
public class ComputationTreeArgumentNode extends ComputationTreeBaseNode {

  private static final int NUM_CANONICALIZED_INSTANCES = 32;
  private static final ComputationTreeArgumentNode[] CANONICALIZED_INSTANCES =
      ArrayUtils.initialize(
          new ComputationTreeArgumentNode[NUM_CANONICALIZED_INSTANCES],
          ComputationTreeArgumentNode::new);

  private final int argumentIndex;

  private ComputationTreeArgumentNode(int argumentIndex) {
    this.argumentIndex = argumentIndex;
  }

  public static ComputationTreeArgumentNode create(int argumentIndex) {
    return argumentIndex < NUM_CANONICALIZED_INSTANCES
        ? CANONICALIZED_INSTANCES[argumentIndex]
        : new ComputationTreeArgumentNode(argumentIndex);
  }

  @Override
  public AbstractValue evaluate(
      IntFunction<AbstractValue> argumentAssignment, AbstractValueFactory abstractValueFactory) {
    return argumentAssignment.apply(argumentIndex);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof ComputationTreeArgumentNode)) {
      return false;
    }
    ComputationTreeArgumentNode node = (ComputationTreeArgumentNode) obj;
    assert argumentIndex >= NUM_CANONICALIZED_INSTANCES
        || node.argumentIndex >= NUM_CANONICALIZED_INSTANCES;
    return argumentIndex == node.argumentIndex;
  }

  @Override
  public int hashCode() {
    return Objects.hash(getClass(), argumentIndex);
  }
}
