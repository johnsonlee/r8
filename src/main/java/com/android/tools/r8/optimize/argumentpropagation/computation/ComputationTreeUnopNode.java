// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.computation;

import com.android.tools.r8.optimize.argumentpropagation.codescanner.BaseInFlow;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodParameter;
import com.android.tools.r8.utils.TraversalContinuation;
import java.util.function.Function;

public abstract class ComputationTreeUnopNode extends ComputationTreeBaseNode {

  final ComputationTreeNode operand;

  ComputationTreeUnopNode(ComputationTreeNode operand) {
    assert !operand.isUnknown();
    this.operand = operand;
  }

  @Override
  public boolean contains(ComputationTreeNode node) {
    return equals(node) || operand.contains(node);
  }

  @Override
  public <TB, TC> TraversalContinuation<TB, TC> traverseBaseInFlow(
      Function<? super BaseInFlow, TraversalContinuation<TB, TC>> fn) {
    return operand.traverseBaseInFlow(fn);
  }

  @Override
  public MethodParameter getSingleOpenVariable() {
    return operand.getSingleOpenVariable();
  }

  boolean internalIsEqualTo(ComputationTreeUnopNode node) {
    return operand.equals(node.operand);
  }

  @Override
  public boolean verifyContainsBaseInFlow(BaseInFlow inFlow) {
    assert operand.verifyContainsBaseInFlow(inFlow);
    return true;
  }
}
