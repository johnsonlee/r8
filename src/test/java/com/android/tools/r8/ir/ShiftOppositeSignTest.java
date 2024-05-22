// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ShiftOppositeSignTest extends TestBase {

  private static final String EXPECTED_RESULT =
      StringUtils.lines(
          "=== 1 ===",
          "shl: 0 134217728 0 512",
          "shr: 0 0 0 0",
          "ushr: 0 0 0 0",
          "shl: 0 64 268435456 0",
          "shr: 0 0 0 0",
          "ushr: 0 0 0 0",
          "=== 123456 ===",
          "shl: 0 0 0 63209472",
          "shr: 0 0 0 241",
          "ushr: 0 0 0 241",
          "shl: 0 7901184 0 0",
          "shr: 0 1929 0 0",
          "ushr: 0 1929 0 0");

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimes().withDexRuntimes().withAllApiLevels().build();
  }

  private final TestParameters parameters;

  public ShiftOppositeSignTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testD8() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  public static class Main {

    public static void main(String[] args) {
      test(1);
      test(123456);
    }

    @NeverInline
    private static void test(int i) {
      System.out.println("=== " + i + " ===");

      System.out.print("shl: ");
      System.out.print(i << -2 << 7);
      System.out.print(" ");
      System.out.print(i << 2 << -7);
      System.out.print(" ");
      System.out.print(i << -2 << -7);
      System.out.print(" ");
      System.out.print(i << 2 << 7);
      System.out.println();

      System.out.print("shr: ");
      System.out.print(i >> -2 >> 7);
      System.out.print(" ");
      System.out.print(i >> 2 >> -7);
      System.out.print(" ");
      System.out.print(i >> -2 >> -7);
      System.out.print(" ");
      System.out.print(i >> 2 >> 7);
      System.out.println();

      System.out.print("ushr: ");
      System.out.print(i >>> -2 >>> 7);
      System.out.print(" ");
      System.out.print(i >>> 2 >>> -7);
      System.out.print(" ");
      System.out.print(i >>> -2 >>> -7);
      System.out.print(" ");
      System.out.print(i >>> 2 >>> 7);
      System.out.println();

      System.out.print("shl: ");
      System.out.print(i << -5 << 31);
      System.out.print(" ");
      System.out.print(i << 5 << -31);
      System.out.print(" ");
      System.out.print(i << -5 << -31);
      System.out.print(" ");
      System.out.print(i << 5 << 31);
      System.out.println();

      System.out.print("shr: ");
      System.out.print(i >> -5 >> 31);
      System.out.print(" ");
      System.out.print(i >> 5 >> -31);
      System.out.print(" ");
      System.out.print(i >> -5 >> -31);
      System.out.print(" ");
      System.out.print(i >> 5 >> 31);
      System.out.println();

      System.out.print("ushr: ");
      System.out.print(i >>> -5 >>> 31);
      System.out.print(" ");
      System.out.print(i >>> 5 >>> -31);
      System.out.print(" ");
      System.out.print(i >>> -5 >>> -31);
      System.out.print(" ");
      System.out.print(i >>> 5 >>> 31);
      System.out.println();
    }
  }
}
