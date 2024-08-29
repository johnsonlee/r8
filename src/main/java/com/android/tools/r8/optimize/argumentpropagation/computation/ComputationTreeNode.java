// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.computation;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodParameter;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.function.Function;

/**
 * Represents a computation tree with no open variables other than the arguments of a given method.
 */
public interface ComputationTreeNode {

  /** Evaluates the current computation tree on the given argument assignment. */
  AbstractValue evaluate(
      AppView<AppInfoWithLiveness> appView,
      Function<MethodParameter, AbstractValue> argumentAssignment);

  MethodParameter getSingleOpenVariable();

  default boolean isArgumentBitSetCompareNode() {
    return false;
  }

  boolean isComputationLeaf();

  default boolean isUnknown() {
    return false;
  }

  default String toStringWithParenthesis() {
    if (isComputationLeaf()) {
      return toString();
    } else {
      return "(" + this + ")";
    }
  }
}
