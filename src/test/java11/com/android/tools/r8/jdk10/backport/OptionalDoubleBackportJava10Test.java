// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk10.backport;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.backports.AbstractBackportTest;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.NoSuchElementException;
import java.util.OptionalDouble;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class OptionalDoubleBackportJava10Test extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return getTestParameters()
        .withDexRuntimesStartingFromIncluding(Version.V7_0_0)
        .withApiLevelsStartingAtIncluding(AndroidApiLevel.N)
        .withCfRuntimesStartingFromIncluding(CfVm.JDK10)
        .enableApiLevelsForCf()
        .build();
  }

  public OptionalDoubleBackportJava10Test(TestParameters parameters) {
    super(parameters, OptionalDouble.class, OptionalDoubleBackportJava10Main.class);
    // Note: The methods in this test exist in android.jar from Android T. When R8 builds targeting
    // Java 11 move these tests to OptionalBackportTest (out of examplesJava10).

    // Available since N.
    ignoreInvokes("empty");
    ignoreInvokes("getAsDouble");
    ignoreInvokes("of");

    registerTarget(AndroidApiLevel.T, 2);
  }

  public static class OptionalDoubleBackportJava10Main {

    public static void main(String[] args) {
      testOrElseThrow();
    }

    private static void testOrElseThrow() {
      OptionalDouble present = OptionalDouble.of(2d);
      assertEquals(2d, present.orElseThrow());

      OptionalDouble absent = OptionalDouble.empty();
      try {
        throw new AssertionError(absent.orElseThrow());
      } catch (NoSuchElementException expected) {
      }
    }

    private static void assertEquals(Object expected, Object actual) {
      if (expected != actual && !expected.equals(actual)) {
        throw new AssertionError("Expected <" + expected + "> but was <" + actual + ">");
      }
    }
  }
}
