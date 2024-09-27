// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.libcore;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class B369739225Test extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static final String EXPECTED_OUTPUT_DALVIK_ART_JDK_UNTIL_11 =
      StringUtils.lines("5", "5", "5", "5", "5");

  private static final String EXPECTED_OUTPUT_JDK_FROM_17 =
      StringUtils.lines("5", "10", "10", "10", "10");

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(
            parameters.isCfRuntime() && parameters.asCfRuntime().isNewerThanOrEqual(CfVm.JDK17),
            r -> r.assertSuccessWithOutput(EXPECTED_OUTPUT_JDK_FROM_17),
            r -> r.assertSuccessWithOutput(EXPECTED_OUTPUT_DALVIK_ART_JDK_UNTIL_11));
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT_DALVIK_ART_JDK_UNTIL_11);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(
            parameters.isCfRuntime() && parameters.asCfRuntime().isNewerThanOrEqual(CfVm.JDK17),
            r -> r.assertSuccessWithOutput(EXPECTED_OUTPUT_JDK_FROM_17),
            r -> r.assertSuccessWithOutput(EXPECTED_OUTPUT_DALVIK_ART_JDK_UNTIL_11));
  }

  static class TestClass {
    long a;
    long b;

    void m() {
      int k = 5;
      k <<= a;
      try {
        String j = "sss";
        java.io.LineNumberReader d =
            new java.io.LineNumberReader(new java.io.CharArrayReader(j.toCharArray()));
        d.skip(Long.MAX_VALUE);
        a = d.getLineNumber();
      } catch (Exception e) {
      }
      System.out.println(k);
    }

    public static void main(String[] n) {
      TestClass test = new TestClass();
      for (int i = 0; i < 5; i++) test.m();
    }
  }
}
