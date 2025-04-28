// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.examples.ifstatements;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.examples.ExamplesTestBase;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class IfStatementsTestRunner extends ExamplesTestBase {

  public IfStatementsTestRunner(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<?> getMainClass() {
    return IfStatements.class;
  }

  @Override
  public String getExpected() {
    return StringUtils.lines(
        "sisnotnull",
        "sisnull",
        "ifCond x != 0",
        "ifCond x < 0",
        "ifCond x <= 0",
        "ifCond x == 0",
        "ifCond x >= 0",
        "ifCond x <= 0",
        "ifCond x != 0",
        "ifCond x >= 0",
        "ifCond x > 0",
        "ifIcmp x != y",
        "ifIcmp x < y",
        "ifIcmp x <= y",
        "ifIcmp x == y",
        "ifIcmp x >= y",
        "ifIcmp x <= y",
        "ifIcmp x != y",
        "ifIcmp x >= y",
        "ifIcmp x > y",
        "ifAcmp a == b",
        "ifAcmp a != b");
  }
}
