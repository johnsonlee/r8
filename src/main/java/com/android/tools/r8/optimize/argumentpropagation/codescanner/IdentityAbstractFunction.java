// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.errors.Unreachable;

public class IdentityAbstractFunction implements AbstractFunction {

  private static final IdentityAbstractFunction INSTANCE = new IdentityAbstractFunction();

  private IdentityAbstractFunction() {}

  static IdentityAbstractFunction get() {
    return INSTANCE;
  }

  @Override
  public ValueState apply(FlowGraphStateProvider flowGraphStateProvider, ConcreteValueState state) {
    return state;
  }

  @Override
  public boolean containsBaseInFlow(BaseInFlow inFlow) {
    throw new Unreachable();
  }

  @Override
  public Iterable<BaseInFlow> getBaseInFlow() {
    throw new Unreachable();
  }

  @Override
  public boolean isIdentity() {
    return true;
  }
}
