// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package backport;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.backports.AbstractBackportTest;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.OptionalDouble;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class OptionalDoubleBackportJava9Test extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return TestBase.getTestParameters()
        .withDexRuntimesStartingFromIncluding(Version.V7_0_0)
        .withApiLevelsStartingAtIncluding(AndroidApiLevel.N)
        .withCfRuntimesStartingFromIncluding(CfVm.JDK9)
        .enableApiLevelsForCf()
        .build();
  }

  public OptionalDoubleBackportJava9Test(TestParameters parameters) {
    super(parameters, OptionalDouble.class, OptionalDoubleBackportJava9Main.class);
    // Note: The methods in this test exist in android.jar from Android T. When R8 builds targeting
    // Java 11 move these tests to OptionalBackportTest (out of examplesJava9).

    // Available since N.
    ignoreInvokes("empty");
    ignoreInvokes("of");

    registerTarget(AndroidApiLevel.T, 4);
  }

  public static class OptionalDoubleBackportJava9Main {

    public static void main(String[] args) {
      testIfPresentOrElseDouble();
      testStreamDouble();
    }

    private static void testIfPresentOrElseDouble() {
      OptionalDouble value = OptionalDouble.of(1.0d);
      OptionalDouble emptyValue = OptionalDouble.empty();
      value.ifPresentOrElse(val -> {}, () -> assertTrue(false));
      emptyValue.ifPresentOrElse(val -> assertTrue(false), () -> {});
    }

    private static void testStreamDouble() {
      OptionalDouble value = OptionalDouble.of(2d);
      OptionalDouble emptyValue = OptionalDouble.empty();
      assertTrue(value.stream().count() == 1);
      assertTrue(emptyValue.stream().count() == 0);
    }

    private static void assertTrue(boolean value) {
      if (!value) {
        throw new AssertionError("Expected <true> but was <false>");
      }
    }
  }
}
