// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.examples.controlflow;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.examples.ExamplesTestBase;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ControlFlowTestRunner extends ExamplesTestBase {

  public ControlFlowTestRunner(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<?> getMainClass() {
    return ControlFlow.class;
  }

  @Override
  public String getExpected() {
    return StringUtils.lines(
        "Fisk",
        "Hest",
        "Fisk 1 1.1 true",
        "Hest 2 2.2 false",
        "Fisk",
        "Hep!",
        "10",
        "10",
        "5",
        "10",
        "5",
        "2",
        "10",
        "10",
        "5",
        "10",
        "5",
        "2",
        "simpleLoop",
        "simpleLoop",
        "count: 0",
        "simpleLoop",
        "count: 0",
        "count: 1",
        "count: 2",
        "count: 3",
        "count: 4",
        "count: 5",
        "count: 6",
        "count: 7",
        "count: 8",
        "count: 9");
  }
}
