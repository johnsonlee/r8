// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class B341618078Test extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static final String EXPECTED_OUTPUT = StringUtils.lines("0", "0");

  @Test
  public void testJvm() throws Exception {
    assumeTrue(parameters.isJvmTestParameters());
    testForJvm(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        // TODO(b/341618078): Should be EXPECTED_OUTPUT.
        .assertSuccessWithOutputLines("1", "1");
  }

  static class TestClass {

    long a;
    long b;

    void c(long l, long d) {
      int e, f = 0, g = 40248, h;
      for (e = h = 1; h < 2; h++) {
        g = f;
        f = e;
      }
      b = g;
    }

    void i(String[] j) {
      c(a, a);
      System.out.println(b);
    }

    static void cSimplified() {
      int e = 1;
      int f = 0;
      int g = 1;
      for (int h = 0; h < 1; h++) {
        g = f;
        f = e;
      }
      System.out.println(g);
    }

    public static void main(String[] k) {
      TestClass m = new TestClass();
      m.i(k);
      TestClass.cSimplified();
    }
  }
}
