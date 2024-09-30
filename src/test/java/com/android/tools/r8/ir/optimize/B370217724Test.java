// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.is;

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
public class B370217724Test extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static final String OUTPUT_JVM8 = StringUtils.lines("8989.358669383371");
  private static final String OUTPUT_FROM_JVM9 = StringUtils.lines("3695.708516962155");
  // Depending on the ART host run environment these results are seen.
  private static final String OUTPUT_ART_1 = StringUtils.lines("5753.491198916323");
  private static final String OUTPUT_ART_2 = StringUtils.lines("10192.673136265881");

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(
            parameters.asCfRuntime().isNewerThanOrEqual(CfVm.JDK9),
            r -> r.assertSuccessWithOutput(OUTPUT_FROM_JVM9),
            r -> r.assertSuccessWithOutput(OUTPUT_JVM8));
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputThatMatches(anyOf(is(OUTPUT_ART_1), is(OUTPUT_ART_2)));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(
            parameters.isCfRuntime(),
            r ->
                r.assertSuccessWithOutput(
                    parameters.asCfRuntime().isNewerThanOrEqual(CfVm.JDK9)
                        ? OUTPUT_FROM_JVM9
                        : OUTPUT_JVM8),
            r -> r.assertSuccessWithOutputThatMatches(anyOf(is(OUTPUT_ART_1), is(OUTPUT_ART_2))));
  }

  static class TestClass {
    public static void main(String[] strArr) {
      double d = 8.65068;
      for (int i = 1; i < 240; ++i) {
        d = 6372.8 * Math.acos(Math.cos(d));
      }
      System.out.println(d);
    }
  }
}
