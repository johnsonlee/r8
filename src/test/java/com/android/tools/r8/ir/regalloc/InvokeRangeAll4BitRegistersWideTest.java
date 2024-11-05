// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.regalloc;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvokeRangeAll4BitRegistersWideTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultDexRuntime().withMaximumApiLevel().build();
  }

  @Test
  public void testD8Debug() throws Exception {
    testForD8()
        .addInnerClasses(getClass())
        .debug()
        .setMinApi(parameters)
        .compile()
        .runDex2Oat(parameters.getRuntime())
        .assertNoVerificationErrors();
  }

  @Test
  public void testD8Release() throws Exception {
    testForD8()
        .addInnerClasses(getClass())
        .release()
        .setMinApi(parameters)
        .compile()
        .runDex2Oat(parameters.getRuntime())
        .assertNoVerificationErrors();
  }

  static class Main {

    long f;

    static void test() {
      int a0 = 0;
      int a1 = 1;
      int a2 = 2;
      int a3 = 3;
      int a4 = 4;
      int a5 = 5;
      int a6 = 6;
      int a7 = 7;
      int a8 = 8;
      int a9 = 9;
      int a10 = 10;
      int a11 = 11;
      int a12 = 12;
      int a13 = 13;
      int a14 = 14;
      int a15 = 15;
      long wide = accept16(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15);
      Main main = null;
      main.f = wide;
    }

    static long accept16(
        int a0,
        int a1,
        int a2,
        int a3,
        int a4,
        int a5,
        int a6,
        int a7,
        int a8,
        int a9,
        int a10,
        int a11,
        int a12,
        int a13,
        int a14,
        int a15) {
      return 0;
    }
  }
}
