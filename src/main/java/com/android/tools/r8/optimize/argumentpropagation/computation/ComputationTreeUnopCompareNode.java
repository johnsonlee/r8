// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.computation;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.SingleNumberValue;
import com.android.tools.r8.ir.code.IfType;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.FlowGraphStateProvider;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodParameter;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Objects;

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
      AppView<AppInfoWithLiveness> appView, FlowGraphStateProvider flowGraphStateProvider) {
    AbstractValue operandValue = operand.evaluate(appView, flowGraphStateProvider);
    if (operandValue.isBottom()) {
      return operandValue;
    }
    return type.evaluate(operandValue, appView);
  }

  @Override
  public boolean isArgumentBitSetCompareNode() {
    if (!type.isEqualsOrNotEquals() || !(operand instanceof ComputationTreeLogicalBinopAndNode)) {
      return false;
    }
    ComputationTreeLogicalBinopAndNode andOperand = (ComputationTreeLogicalBinopAndNode) operand;
    return andOperand.left instanceof MethodParameter
        && andOperand.right instanceof SingleNumberValue;
  }

  public ComputationTreeUnopCompareNode negate() {
    return new ComputationTreeUnopCompareNode(operand, type.inverted());
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

  @Override
  public String toString() {
    return operand.toStringWithParenthesis() + " " + type.getSymbol() + " 0";
  }
}
