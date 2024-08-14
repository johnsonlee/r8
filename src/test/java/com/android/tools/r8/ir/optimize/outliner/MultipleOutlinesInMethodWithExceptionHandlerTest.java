// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.outliner;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.AssertUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MultipleOutlinesInMethodWithExceptionHandlerTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static final String EXPECTED_OUTPUT = StringUtils.lines("18");

  @Test
  public void testR8() throws Exception {
    AssertUtils.assertFailsCompilation(
        () ->
            testForR8(parameters.getBackend())
                .addInnerClasses(getClass())
                .addKeepMainRule(TestClass.class)
                .setMinApi(parameters)
                .enableInliningAnnotations()
                .addOptionsModification(
                    options -> {
                      // To trigger outlining.
                      options.outline.threshold = 2;
                      options.outline.maxSize = 3;
                    })
                .compile()
                .run(parameters.getRuntime(), TestClass.class)
                .assertSuccessWithOutput(EXPECTED_OUTPUT));
  }

  static class TestClass {

    @NeverInline
    private static void m(int a, int b, int c, int d) {
      try {
        // The test is expecting an outline with mul, mul, add instructions.
        System.out.println((a * b * c + d) * (d * c * b + a));
      } catch (ArithmeticException e) {
        System.out.println(e);
      }
    }

    public static void main(String[] args) {
      m(args.length, args.length + 1, args.length + 2, args.length + 3);
    }
  }
}
