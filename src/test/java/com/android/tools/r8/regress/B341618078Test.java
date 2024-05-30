// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
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

  private static final String EXPECTED_OUTPUT = StringUtils.lines("0", "0", "1");

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
    parameters.assumeDexRuntime();
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
        .compile()
        .inspect(this::assertLoopUnrolled)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  private void assertLoopUnrolled(CodeInspector inspector) {
    // All control flow has been removed.
    inspector.clazz(TestClass.class).forAllMethods(m ->
        assertTrue(m.streamInstructions().noneMatch(i -> i.isGoto() || i.isIf())));
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

    static void cChainedSimplified() {
      int a = 1;
      int b = 2;
      int c = 3;
      int d = 4;
      for (int h = 0; h < 1; h++) {
        b = a;
        c = b;
        d = c;
      }
      System.out.println(d);
    }

    public static void main(String[] k) {
      TestClass m = new TestClass();
      m.i(k);
      cSimplified();
      cChainedSimplified();
    }
  }
}
