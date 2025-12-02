// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk25.backport;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.desugar.backports.AbstractBackportTest;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class MathBackportJava25Test extends AbstractBackportTest {

  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return TestBase.getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK25)
        .withDexRuntimes()
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  public MathBackportJava25Test(TestParameters parameters) {
    super(parameters, Math.class, MathBackportJava25Main.class);
  }

  public static class MathBackportJava25Main {

    public static void main(String[] args) {
      testPowExact();
    }

    static void testPowExact() {
      try {
        Math.powExact(42, -1);
        fail();
      } catch (ArithmeticException ae) {
      }

      assertEquals(1, Math.powExact(0, 0));
      assertEquals(0, Math.powExact(0, 1));
      assertEquals(0, Math.powExact(0, 2));

      assertEquals(1, Math.powExact(-1, 0));
      assertEquals(-1, Math.powExact(-1, 1));
      assertEquals(1, Math.powExact(-1, 2));

      assertEquals(1, Math.powExact(1, 0));
      assertEquals(1, Math.powExact(1, 1));
      assertEquals(1, Math.powExact(1, 2));

      assertEquals(1, Math.powExact(2, 0));
      assertEquals(2, Math.powExact(2, 1));
      assertEquals(4, Math.powExact(2, 2));

      assertEquals(1, Math.powExact(Integer.MIN_VALUE, 0));
      assertEquals(-2147483648, Math.powExact(Integer.MIN_VALUE, 1));
      try {
        Math.powExact(Integer.MIN_VALUE, 2);
        fail();
      } catch (ArithmeticException ae) {
      }

      assertEquals(1, Math.powExact(Integer.MAX_VALUE, 0));
      assertEquals(2147483647, Math.powExact(Integer.MAX_VALUE, 1));
      try {
        Math.powExact(Integer.MAX_VALUE, 2);
        fail();
      } catch (ArithmeticException ae) {
      }

      assertEquals(4096, Math.powExact(256 / 16, 3));
      assertEquals(512, Math.powExact(256 / 32, 3));
    }

    static void assertEquals(int x, int y) {
      if (x != y) {
        throw new RuntimeException("Not equals " + x + " and " + y);
      }
    }

    static void fail() {
      throw new RuntimeException("Test fails.");
    }
  }
}
