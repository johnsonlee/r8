// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.codescanner;

public interface AbstractFunction extends InFlow {

  static IdentityAbstractFunction identity() {
    return IdentityAbstractFunction.get();
  }

  static UnknownAbstractFunction unknown() {
    return UnknownAbstractFunction.get();
  }

  /**
   * Applies the current abstract function to the given {@param state}.
   *
   * <p>It is guaranteed by the caller that the given {@param state} is the abstract state for the
   * field or parameter this function depends on, i.e., the node returned by {@link
   * #getBaseInFlow()}.
   */
  NonEmptyValueState apply(ConcreteValueState state);

  /**
   * Returns the (single) program field or parameter graph node that this function depends on. Upon
   * any change to the abstract state of this graph node this abstract function must be
   * re-evaluated.
   */
  InFlow getBaseInFlow();

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
}
