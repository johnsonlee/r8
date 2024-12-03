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
import java.util.Optional;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class OptionalBackportJava9Test extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return TestBase.getTestParameters()
        .withDexRuntimesStartingFromIncluding(Version.V7_0_0)
        .withApiLevelsStartingAtIncluding(AndroidApiLevel.N)
        .withCfRuntimesStartingFromIncluding(CfVm.JDK9)
        .enableApiLevelsForCf()
        .build();
  }

  public OptionalBackportJava9Test(TestParameters parameters) {
    super(parameters, Optional.class, OptionalBackportJava9Main.class);
    // Note: The methods in this test exist in android.jar from Android T. When R8 builds targeting
    // Java 11 move these tests to OptionalBackportTest (out of examplesJava9).

    // Available since N.
    ignoreInvokes("empty");
    ignoreInvokes("of");

    registerTarget(AndroidApiLevel.T, 10);
  }

  public static class OptionalBackportJava9Main {

    public static void main(String[] args) {
      testOr();
      testOrNull();
      testIfPresentOrElse();
      testStream();
    }

    private static void testOr() {
      Optional<String> value = Optional.of("value");
      Optional<String> defaultValue = Optional.of("default");
      Optional<String> emptyValue = Optional.empty();
      Optional<String> result;

      result = value.or(() -> defaultValue);
      assertTrue(value == result);
      result = emptyValue.or(() -> defaultValue);
      assertTrue(result == defaultValue);
    }

    private static void testOrNull() {
      Optional<String> value = Optional.of("value");
      Optional<String> emptyValue = Optional.empty();

      try {
        value.or(null);
        fail();
      } catch (NullPointerException e) {
      }

      try {
        emptyValue.or(null);
        fail();
      } catch (NullPointerException e) {
      }

      try {
        value.or(() -> null);
      } catch (NullPointerException e) {
        fail();
      }

      try {
        emptyValue.or(() -> null);
        fail();
      } catch (NullPointerException e) {
      }
    }

    private static void testIfPresentOrElse() {
      Optional<String> value = Optional.of("value");
      Optional<String> emptyValue = Optional.empty();
      value.ifPresentOrElse(val -> {}, () -> assertTrue(false));
      emptyValue.ifPresentOrElse(val -> assertTrue(false), () -> {});
    }

    private static void testStream() {
      Optional<String> value = Optional.of("value");
      Optional<String> emptyValue = Optional.empty();
      assertTrue(value.stream().count() == 1);
      assertTrue(emptyValue.stream().count() == 0);
    }

    private static void assertTrue(boolean value) {
      if (!value) {
        throw new AssertionError("Expected <true> but was <false>");
      }
    }

    private static void fail() {
      throw new AssertionError("Failure.");
    }
  }
}
