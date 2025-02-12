// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.deadcode;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

// This is a regression test for b/395489597.
@RunWith(Parameterized.class)
public class DifferentAssumptionsOnReturnValuesTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static final String EXPECTED_OUTPUT = StringUtils.lines("f(null)", "f returned null");
  private static final String NOT_EXPECTED_OUTPUT =
      StringUtils.lines("f(null)", "f returned non null");

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), TestClass.class)
        // TODO(b/395489597): This should not happen.
        .assertSuccessWithOutput(NOT_EXPECTED_OUTPUT);
  }

  static class TestClass {
    @NeverInline
    public static Object f(Object o) {
      System.out.println("f(" + ((o == null) ? "null" : "not null") + ")");
      if (o != null) {
        return o;
      }
      return o;
    }

    public static void main(String[] args) {
      if (f(System.currentTimeMillis() > 0 ? null : new Object()) != null) {
        System.out.println("f returned non null");
      } else {
        System.out.println("f returned null");
      }
    }
  }
}
