// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.errors.Unreachable;

public class UnknownAbstractFunction implements AbstractFunction {

  private static final UnknownAbstractFunction INSTANCE = new UnknownAbstractFunction();

  private UnknownAbstractFunction() {}

  static UnknownAbstractFunction get() {
    return INSTANCE;
  }

  @Override
  public NonEmptyValueState apply(ConcreteValueState state) {
    return ValueState.unknown();
  }

  @Override
  public InFlow getBaseInFlow() {
    throw new Unreachable();
  }

  @Override
  public boolean isUnknownAbstractFunction() {
    return true;
  }
}
