// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.compose;

import com.android.tools.r8.optimize.argumentpropagation.codescanner.AbstractFunction;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.InFlow;

public class UpdateChangedFlagsAbstractFunction implements AbstractFunction {

  @SuppressWarnings("UnusedVariable")
  private final InFlow inFlow;

  public UpdateChangedFlagsAbstractFunction(InFlow inFlow) {
    this.inFlow = inFlow;
  }
}
