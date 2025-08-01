// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress.b426351560;

import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.regress.b426351560.testclasses.Regress426351560TestClasses;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Regress426351560Test extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().withPartialCompilation().build();
  }

  private static final String EXPECTED_OUTPUT = StringUtils.lines("Hello, world!");

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(Regress426351560TestClasses.class, getClass())
        .addKeepRules("-keep class " + Main.class.getTypeName() + " { *; }")
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  public static class Main extends Regress426351560TestClasses.A {
    public static void main(String[] strArr) {
      Main test = new Main();
      System.out.println(test.defaultMethod());
    }
  }
}
