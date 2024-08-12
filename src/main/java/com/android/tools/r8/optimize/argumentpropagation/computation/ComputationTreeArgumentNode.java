// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.computation;

import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.AbstractValueFactory;
import com.android.tools.r8.utils.ArrayUtils;
import java.util.function.IntFunction;

/** Represents the read of an argument. */
public class ComputationTreeArgumentNode implements ComputationTreeNode {

  private static final ComputationTreeArgumentNode[] CANONICALIZED_INSTANCES =
      ArrayUtils.initialize(new ComputationTreeArgumentNode[32], ComputationTreeArgumentNode::new);

  private final int argumentIndex;

  private ComputationTreeArgumentNode(int argumentIndex) {
    this.argumentIndex = argumentIndex;
  }

  public static ComputationTreeArgumentNode create(int argumentIndex) {
    return argumentIndex < 32
        ? CANONICALIZED_INSTANCES[argumentIndex]
        : new ComputationTreeArgumentNode(argumentIndex);
  }

  @Override
  public AbstractValue evaluate(
      IntFunction<AbstractValue> argumentAssignment, AbstractValueFactory abstractValueFactory) {
    return argumentAssignment.apply(argumentIndex);
  }
}
