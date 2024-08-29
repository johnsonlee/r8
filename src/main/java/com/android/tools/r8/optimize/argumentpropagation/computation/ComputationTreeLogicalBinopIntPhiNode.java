// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.computation;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.AbstractValueJoiner.AbstractValueConstantPropagationJoiner;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodParameter;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Objects;
import java.util.function.Function;

public class ComputationTreeLogicalBinopIntPhiNode extends ComputationTreeLogicalBinopNode {

  private final ComputationTreeNode condition;

  private ComputationTreeLogicalBinopIntPhiNode(
      ComputationTreeNode condition, ComputationTreeNode left, ComputationTreeNode right) {
    super(left, right);
    this.condition = condition;
  }

  public static ComputationTreeNode create(
      ComputationTreeNode condition, ComputationTreeNode left, ComputationTreeNode right) {
    if (left.isUnknown() && right.isUnknown()) {
      return AbstractValue.unknown();
    }
    return new ComputationTreeLogicalBinopIntPhiNode(condition, left, right);
  }

  @Override
  public AbstractValue evaluate(
      AppView<AppInfoWithLiveness> appView,
      Function<MethodParameter, AbstractValue> argumentAssignment) {
    AbstractValue result = condition.evaluate(appView, argumentAssignment);
    if (result.isBottom()) {
      return AbstractValue.bottom();
    } else if (result.isTrue()) {
      return left.evaluate(appView, argumentAssignment);
    } else if (result.isFalse()) {
      return right.evaluate(appView, argumentAssignment);
    } else {
      AbstractValueConstantPropagationJoiner joiner =
          appView.getAbstractValueConstantPropagationJoiner();
      AbstractValue leftValue = left.evaluate(appView, argumentAssignment);
      AbstractValue rightValue = right.evaluate(appView, argumentAssignment);
      return joiner.join(leftValue, rightValue, TypeElement.getInt());
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof ComputationTreeLogicalBinopIntPhiNode)) {
      return false;
    }
    ComputationTreeLogicalBinopIntPhiNode node = (ComputationTreeLogicalBinopIntPhiNode) obj;
    return condition.equals(node.condition) && internalIsEqualTo(node);
  }

  @Override
  public int hashCode() {
    return Objects.hash(getClass(), condition, left, right);
  }

  @Override
  public String toString() {
    return condition.toStringWithParenthesis()
        + " ? "
        + left.toStringWithParenthesis()
        + " : "
        + right.toStringWithParenthesis();
  }
}
