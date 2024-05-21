// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class B341476044Test extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static final List<String> EXPECTED_OUTPUT =
      ImmutableList.of(
          "0",
          "-2360000",
          "-2360024",
          "-2360048",
          "-2360072",
          "-2360096",
          "-2360120",
          "-2359888",
          "-2359912",
          "-2359936");

  private static final List<String> NOT_EXPECTED_OUTPUT = new ArrayList<>(EXPECTED_OUTPUT);

  static {
    NOT_EXPECTED_OUTPUT.set(7, "-2360144");
  }

  @Test
  public void testJvm() throws Exception {
    assumeTrue(parameters.isJvmTestParameters());
    testForJvm(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(
            parameters.isDexRuntime()
                && parameters.getDexRuntimeVersion().isNewerThanOrEqual(Version.V13_0_0),
            // TODO(b/341476044): Should be EXPECTED_OUTPUT.
            r -> r.assertSuccessWithOutputLines(NOT_EXPECTED_OUTPUT),
            r -> r.assertSuccessWithOutputLines(EXPECTED_OUTPUT));
  }

  static class TestClass {
    long a;
    byte b;
    int u;
    long c;
    int v;

    void e(int f, int g) {
      c = f;
    }

    void h(long j, int k, int l) {
      e(k, k);
    }

    public static void main(String[] m) {
      try {
        TestClass n = new TestClass();
        for (int i = 0; i < 10; i++) n.o(m);
      } catch (Exception ex) {
      }
    }

    void p(int w) {
      int q, r = 24;
      h(a, w, w);
      b -= v;
      for (q = 1; q < 12; q++) v = r;
    }

    void o(String[] s) {
      double d = 118.89497;
      p(u);
      u = b;
      for (int t = 0; t < 20000; ++t) u -= d;
      System.out.println("" + c);
    }
  }
}
