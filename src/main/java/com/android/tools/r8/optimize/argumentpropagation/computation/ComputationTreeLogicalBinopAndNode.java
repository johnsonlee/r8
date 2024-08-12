// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.computation;

import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.AbstractValueFactory;
import com.android.tools.r8.ir.analysis.value.arithmetic.AbstractCalculator;
import java.util.Objects;
import java.util.function.IntFunction;

public class ComputationTreeLogicalBinopAndNode extends ComputationTreeLogicalBinopNode {

  private ComputationTreeLogicalBinopAndNode(ComputationTreeNode left, ComputationTreeNode right) {
    super(left, right);
  }

  public static ComputationTreeNode create(ComputationTreeNode left, ComputationTreeNode right) {
    if (left.isUnknown() && right.isUnknown()) {
      return AbstractValue.unknown();
    }
    return new ComputationTreeLogicalBinopAndNode(left, right);
  }

  @Override
  public AbstractValue evaluate(
      IntFunction<AbstractValue> argumentAssignment, AbstractValueFactory abstractValueFactory) {
    assert getNumericType().isInt();
    AbstractValue leftValue = left.evaluate(argumentAssignment, abstractValueFactory);
    AbstractValue rightValue = right.evaluate(argumentAssignment, abstractValueFactory);
    return AbstractCalculator.andIntegers(abstractValueFactory, leftValue, rightValue);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof ComputationTreeLogicalBinopAndNode)) {
      return false;
    }
    ComputationTreeLogicalBinopAndNode node = (ComputationTreeLogicalBinopAndNode) obj;
    return internalIsEqualTo(node);
  }

  @Override
  public int hashCode() {
    return Objects.hash(getClass(), left, right);
  }
}
