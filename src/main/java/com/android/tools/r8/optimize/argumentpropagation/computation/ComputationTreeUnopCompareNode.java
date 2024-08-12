// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.computation;

import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.AbstractValueFactory;
import com.android.tools.r8.ir.code.IfType;
import java.util.Objects;
import java.util.function.IntFunction;

public class ComputationTreeUnopCompareNode extends ComputationTreeUnopNode {

  private final IfType type;

  private ComputationTreeUnopCompareNode(ComputationTreeNode operand, IfType type) {
    super(operand);
    this.type = type;
  }

  public static ComputationTreeNode create(ComputationTreeNode operand, IfType type) {
    if (operand.isUnknown()) {
      return AbstractValue.unknown();
    }
    return new ComputationTreeUnopCompareNode(operand, type);
  }

  @Override
  public AbstractValue evaluate(
      IntFunction<AbstractValue> argumentAssignment, AbstractValueFactory abstractValueFactory) {
    AbstractValue operandValue = operand.evaluate(argumentAssignment, abstractValueFactory);
    return type.evaluate(operandValue, abstractValueFactory);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof ComputationTreeUnopCompareNode)) {
      return false;
    }
    ComputationTreeUnopCompareNode node = (ComputationTreeUnopCompareNode) obj;
    return type == node.type && internalIsEqualTo(node);
  }

  @Override
  public int hashCode() {
    return Objects.hash(getClass(), operand, type);
  }
}
