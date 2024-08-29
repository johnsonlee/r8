// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.TraversalContinuation;

public interface AbstractFunction extends InFlow {

  static IdentityAbstractFunction identity() {
    return IdentityAbstractFunction.get();
  }

  /**
   * Applies the current abstract function to its declared inputs (from {@link
   * #traverseBaseInFlow}).
   *
   * <p>It is guaranteed by the caller that the given {@param state} is the abstract state for the
   * field or parameter that caused this function to be reevaluated. If this abstract function takes
   * a single input, then {@param state} is guaranteed to be the state for the node returned by
   * {@link #traverseBaseInFlow}, and {@param flowGraphStateProvider} should never be used.
   *
   * <p>Abstract functions that depend on multiple inputs can lookup the state for each input in
   * {@param flowGraphStateProvider}. Attempting to lookup the state of a non-declared input is an
   * error.
   */
  ValueState apply(
      AppView<AppInfoWithLiveness> appView,
      FlowGraphStateProvider flowGraphStateProvider,
      ConcreteValueState inState,
      DexType outStaticType);

  default boolean usesFlowGraphStateProvider() {
    return false;
  }

  @Override
  default boolean isAbstractFunction() {
    return true;
  }

  @Override
  default AbstractFunction asAbstractFunction() {
    return this;
  }

  default boolean isIdentity() {
    return false;
  }

  default boolean isUpdateChangedFlags() {
    return false;
  }

  /** Verifies that {@param stoppingCriterion} is a declared input of this abstract function. */
  default boolean verifyContainsBaseInFlow(BaseInFlow stoppingCriterion) {
    assert traverseBaseInFlow(
            inFlow -> TraversalContinuation.breakIf(inFlow.equals(stoppingCriterion)))
        .shouldBreak();
    return true;
  }
}
