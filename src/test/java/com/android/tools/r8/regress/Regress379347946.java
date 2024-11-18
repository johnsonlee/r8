// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import java.util.Random;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Regress379347946 extends TestBase {

  String EXPECTED = "6035364";

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntimesStartingFromIncluding(Version.V4_4_4)
        .withMaximumApiLevel()
        .build();
  }

  public Regress379347946(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(Regress379347946.class)
        .addKeepMainRule(TestClass.class)
        .addDontObfuscate()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addInnerClasses(Regress379347946.class)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testD8Release() throws Exception {
    testForD8(parameters.getBackend())
        .addInnerClasses(Regress379347946.class)
        .setMinApi(parameters)
        .release()
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  static class TestClass {
    static int N = 400;
    long instanceCount;
    int iFld;
    float fFld;
    static int[][] iArrFld = new int[N][N];
    long vMeth_check_sum;

    short sMeth(int i10, int i11, int i12) {
      int i13, i14, i15 = 61554, i16 = 47, i17 = 78;
      short s = 10384;
      i13 = 1;
      do
        for (i14 = 1; 13 > i14; ++i14) {
          double d1;
          d1 = i13;
          for (i16 = 1; i16 < 2; i16++) {
            i15 += i16;
            i17 = i11;
            i10 = (int) instanceCount;
            s -= d1;
            i11 -= 42418;
          }
        }
      while (++i13 < 123);
      long meth_res = i10 + i15 + i16 + i17 + s;
      return (short) meth_res;
    }

    void vMeth(long l1, long l2) {
      int i9 = 12;
      long[][] lArr = new long[N][N];
      for (int j = 0; j < lArr.length; j++) {
        for (int j1 = 0; j1 < lArr[j].length; j1++) {
          lArr[j][j1] = (j1 % 2 == 0) ? 21645L + j1 : 21645L - j1;
        }
      }
      iArrFld[1][1] -= sMeth(i9, i9, i9) * i9;
      long sum = 0;
      for (int j = 0; j < lArr.length; j++) {
        long sum1 = 0;
        for (int j1 = 0; j1 < lArr[j].length; j1++) {
          sum1 += (lArr[j][j1] / (j1 + 1) + lArr[j][j1] % (j1 + 1));
        }
        sum += sum1;
      }
      vMeth_check_sum += sum;
    }

    int iMeth(int i7, long l, int i8) {
      vMeth(instanceCount, l);
      long meth_res = l;
      return (int) meth_res;
    }

    void mainTest(String[] strArr1) {
      int i = 90;
      int i1;
      int i2 = 153;
      int i3 = 15;
      int i4 = 1;
      int i5 = -85;
      int i6 = 57107;
      int[][] iArr = new int[N][N];
      double d = 98.33365;
      byte by = 11;
      long l3 = 1312947818L;
      short s2 = 19614;
      do
        for (i1 = 5; 194 > i1; ++i1) {
          iArr[i][i] = i3;
          d = Math.min(iFld, by);
          for (; i5 < 2; ++i5) {
            String[] var19 = {};
            c59.main(var19);
            i3 *= iMeth(iFld, instanceCount, i6);
            switch (i) {
              case 205:
                System.out.println("i = " + i);
                i5 = i2;
              case 283:
                by *= l3;
              case 352:
                s2 = (short) instanceCount;
              case 230:
                iArrFld[196] = FuzzerUtils.int1array(N, 251);
              case 362:
                l3 += fFld;
              case 386:
                i2 = i6;
              case 392:
                i6 = i4;
              case 332:
                i4 <<= s2;
            }
          }
        }
      while (++i < 129);
      double d_print = Double.doubleToLongBits(d);
      long sum = 0;
      for (int j = 0; j < iArrFld.length; j++) {
        long sum1 = 0;
        for (int j1 = 0; j1 < iArrFld[j].length; j1++) {
          sum1 += (iArrFld[j][j1] / (j1 + 1) + iArrFld[j][j1] % (j1 + 1));
        }
        sum += sum1;
      }
      long iArrFld_check_sum = sum;
      System.out.println(iArrFld_check_sum);
    }

    public static void main(String[] strArr) {
      TestClass _instance = new TestClass();
      _instance.mainTest(strArr);
    }
  }

  static class c59 {
    static void main(String[] args) {
      c59Class.main(args);
    }

    static class c59Class {
      static void bar() {
        try (AutoCloseableResource resource = new c59.AutoCloseableResource()) {
        } catch (Exception e) {
        }
      }

      static void main(String[] args) {
        c59Class.bar();
      }
    }

    static class AutoCloseableResource implements AutoCloseable {
      public void close() {
        throw new java.lang.RuntimeException();
      }
    }
  }

  static class FuzzerUtils {
    public static Random random = new Random(1);

    public static int[] int1array(int sz, int seed) {
      int[] ret = new int[sz];
      for (int j = 0; j < ret.length; j++) {
        ret[j] = (j % 2 == 0) ? seed + j : seed - j;
      }
      return ret;
    }
  }
}
