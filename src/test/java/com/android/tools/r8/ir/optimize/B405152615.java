// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class B405152615 extends TestBase {

  private static String expectedOutput;

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @BeforeClass
  public static void beforeClass() throws Exception {
    expectedOutput =
        testForJvm(getStaticTemp())
            .addTestClasspath()
            .run(TestRuntime.getDefaultCfRuntime(), Main.class)
            .getStdOut();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(expectedOutput);
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters)
        .addInnerClasses(getClass())
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(expectedOutput);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/405152615): Investigate change in output.
        .applyIf(
            parameters.isDexRuntimeVersionNewerThanOrEqual(Version.V13_0_0),
            rr -> rr.assertSuccessWithOutputThatMatches(not(equalTo(expectedOutput))),
            rr -> rr.assertSuccessWithOutput(expectedOutput));
  }

  static class Main {

    public static void main(String[] strArr) {
      int i1 = 1;
      do
        try {
          java.io.Writer v1 = null;
          m(v1);
        } catch (Exception eeeee) {
        }
      while (++i1 < 50);
      System.out.println("over");
    }

    static void m(java.io.Writer input) {
      new String();
      char[] cArr = new char[10];
      int i2 = cArr.length;
      int i3 = i2;
      do {
        cArr[--i2] = '0';
        i3 /= 10;
      } while (i3 > 0);
      System.out.println(cArr);
      String s2 = new String();
      String s3 = "sss" + s2 + "sss";
      try {
        input.append(s3);
      } catch (java.io.IOException e) {
      }
    }
  }
}
