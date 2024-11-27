// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package backport;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.backports.AbstractBackportTest;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.OptionalLong;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class OptionalLongBackportJava11Test extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return getTestParameters()
        .withDexRuntimesStartingFromIncluding(Version.V7_0_0)
        .withApiLevelsStartingAtIncluding(AndroidApiLevel.N)
        .withCfRuntimesStartingFromIncluding(CfVm.JDK11)
        .enableApiLevelsForCf()
        .build();
  }

  public OptionalLongBackportJava11Test(TestParameters parameters) {
    super(parameters, OptionalLong.class, OptionalLongBackportJava11Main.class);
    // Note: The methods in this test exist in android.jar from Android T. When R8 builds targeting
    // Java 11 move these tests to OptionalBackportTest (out of examplesJava11).

    // Available since N.
    ignoreInvokes("empty");
    ignoreInvokes("of");

    registerTarget(AndroidApiLevel.T, 2);
  }

  public static class OptionalLongBackportJava11Main {

    public static void main(String[] args) {
      testIsEmpty();
    }

    private static void testIsEmpty() {
      OptionalLong present = OptionalLong.of(2L);
      assertFalse(present.isEmpty());

      OptionalLong absent = OptionalLong.empty();
      assertTrue(absent.isEmpty());
    }

    private static void assertTrue(boolean value) {
      if (!value) {
        throw new AssertionError("Expected <true> but was <false>");
      }
    }

    private static void assertFalse(boolean value) {
      if (value) {
        throw new AssertionError("Expected <false> but was <true>");
      }
    }
  }
}
