// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class B418719343Test extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("-21130949", "over");
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters)
        .addInnerClasses(getClass())
        .release()
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("-21130949", "over");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepClassAndMembersRules(Main.class)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/418568424): Should succeed with expected output.
        .applyIf(
            parameters.isDexRuntime()
                && parameters.getDexRuntimeVersion().isNewerThanOrEqual(Version.V14_0_0),
            rr -> rr.assertSuccessWithOutputLines("-21090195", "over"),
            rr -> rr.assertSuccessWithOutputLines("-21130949", "over"));
  }

  static class Main {
    int N = 256;
    long instanceCount;
    int iFld;
    double dFld;
    int iFld1;
    long lFld;
    long lFld1;
    float fFld;

    void vMeth() {
      double[][] dArr = new double[N][N];
    }

    long lMeth(int i9, int i10, long l) {
      int i11, i12 = 31206, i22, i25 = 159, i26, i27 = 4;
      byte[] byArr = new byte[N];
      try {
        for (i11 = 14; i11 < 17; ++i11) {
          vMeth();
          i22 = 1;
          while (++i22 < 3) {
            i10 += i22;
            i12 = iFld1;
          }
          for (i26 = 1; i26 < 4; i26++) {
            instanceCount += i12;
            lFld += lFld1;
            i27 -= i22;
            lFld1 >>= i27;
          }
        }
      } catch (NullPointerException exc2) {
        int var9 = 0;
        do var9++;
        while (var9 < 10000);
      } catch (ArithmeticException exc3) {
        fFld *= i25;
      }
      long meth_res = i9 + i10 + i25 + FuzzerUtils.checkSum(byArr);
      return meth_res;
    }

    int iMeth(int i3, int i4) {
      int i7;
      for (i7 = 1; i7 < 4; ++i7) dFld %= lMeth(60, 38876, lFld1);
      long meth_res = i3;
      return (int) meth_res;
    }

    void mainTest(String[] strArr1) {
      int i = 20377, i1, i2 = 217;
      for (i1 = 178; i1 > 175; --i1) {
        iFld -= iMeth(i2, i);
        lFld1 = -112;
      }
      iFld += lFld;
      i *= iFld;
      System.out.println(i);
    }

    public static void main(String[] strArr) {
      Main _instance = new Main();
      _instance.mainTest(strArr);
      System.out.println("over");
    }
  }

  static class FuzzerUtils {
    public static long checkSum(byte[] a) {
      long sum = 0;
      for (int j = 0; j < a.length; j++) {
        sum += (byte) (a[j] / (j + 1) + a[j] % (j + 1));
      }
      return sum;
    }
  }
}
