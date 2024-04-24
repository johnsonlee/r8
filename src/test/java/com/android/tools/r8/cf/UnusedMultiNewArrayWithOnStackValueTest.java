// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Regression test for b/335663479. */
@RunWith(Parameterized.class)
public class UnusedMultiNewArrayWithOnStackValueTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public UnusedMultiNewArrayWithOnStackValueTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addKeepMainRule(TestClass.class)
        .addDontWarn(A.class)
        .setMinApi(parameters)
        .compile()
        .addRunClasspathClasses(A.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("42");
  }

  static class TestClass {

    public static void main(String[] args) {
      // The array instructions cannot be removed because A is not provided as a program class.
      int dim = 42;
      A[][] local = new A[dim][dim];
      local = new A[dim][dim];
      System.out.println(local.length);
    }
  }

  static class A {}
}
