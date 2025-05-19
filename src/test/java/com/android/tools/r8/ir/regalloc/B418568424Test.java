// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
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
public class B418568424Test extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepClassAndMembersRules(Main.class)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccess();
  }

  static class Main {

    final int N = 256;
    int iFld;
    long vMeth2_check_sum;

    void vMeth2(long l1, int i7, long l2) {
      long l3;
      int i8 = -5;
      int i10 = 13;
      int i11 = -234;
      int i13 = -5;
      int i14 = 11;
      int var6 = 0;
      int var7 = 0;
      while (var7 < 8) {
        int[][] iArr = new int[N][var6];
        var6 = N;
        var7++;
      }
      int[][] iArr = new int[N][N];
      double d = 6.51399, d1 = 106.34938;
      float f = 36.859F;
      boolean b = true;
      byte by = 35;
      for (l3 = 9; l3 < 12; ++l3)
        for (d = 1; d < 4; d++)
          switch ((int) d) {
            case 64:
              for (; i11 < 4; ++i11) {
                i7 -= i7;
                i8 *= i10;
              }
              for (; i13 < 4; ++i13) {
                i14 -= i14;
                iFld += (int) l3;
                f += i7;
                i10 += i13;
                d1 += i14;
                l2 = iFld;
              }
            case 54:
              byte var5 = (byte) i8;
              by = var5;
          }
      vMeth2_check_sum +=
          l1 + l2 + d + f + i14 + d1 + (b ? 1 : 0) + by + FuzzerUtils.checkSum(iArr);
    }

    public static void main(String[] args) {
      System.out.println("Hello world");
    }
  }

  static class FuzzerUtils {

    public static long checkSum(int[][] a) {
      long sum = 0;
      for (int j = 0; j < a.length; j++) {
        sum += checkSum(a[j]);
      }
      return sum;
    }

    public static long checkSum(int[] a) {
      long sum = 0;
      for (int j = 0; j < a.length; j++) {
        sum += (a[j] / (j + 1) + a[j] % (j + 1));
      }
      return sum;
    }
  }
}
