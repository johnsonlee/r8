// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk17.backport;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MathBackportJava17Test extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection parameters() {
    return getTestParameters()
        .withDexRuntimes()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK17)
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  @Test
  public void test() throws Exception {
    testForDesugaring(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccess();
  }

  public static class TestClass {
    public static void main(String[] args) {
      // The methods are actually from Java 15, but we can test them from Java 17.
      testAbsExactInteger();
      testAbsExactLong();
    }

    private static void testAbsExactInteger() {
      assertEquals(42, Math.absExact(42));
      assertEquals(42, Math.absExact(-42));
      assertEquals(Integer.MAX_VALUE, Math.absExact(Integer.MAX_VALUE));
      try {
        throw new AssertionError(Math.absExact(Integer.MIN_VALUE));
      } catch (ArithmeticException expected) {

      }
    }

    private static void testAbsExactLong() {
      assertEquals(42L, Math.absExact(42L));
      assertEquals(42L, Math.absExact(-42L));
      assertEquals(Long.MAX_VALUE, Math.absExact(Long.MAX_VALUE));
      try {
        throw new AssertionError(Math.absExact(Long.MIN_VALUE));
      } catch (ArithmeticException expected) {

      }
    }

    private static void assertEquals(int expected, int actual) {
      if (expected != actual) {
        throw new AssertionError("Expected <" + expected + "> but was <" + actual + '>');
      }
    }

    private static void assertEquals(long expected, long actual) {
      if (expected != actual) {
        throw new AssertionError("Expected <" + expected + "> but was <" + actual + '>');
      }
    }
  }
}
