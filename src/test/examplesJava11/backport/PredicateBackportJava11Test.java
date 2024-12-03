// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package backport;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.backports.AbstractBackportTest;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.function.Predicate;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class PredicateBackportJava11Test extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return getTestParameters()
        .withDexRuntimesStartingFromIncluding(Version.V7_0_0)
        .withApiLevelsStartingAtIncluding(AndroidApiLevel.N)
        .withCfRuntimesStartingFromIncluding(CfVm.JDK11)
        .enableApiLevelsForCf()
        .build();
  }

  public PredicateBackportJava11Test(TestParameters parameters) {
    super(parameters, OptionalLong.class, PredicateBackportJava11Main.class);

    // Available since N as part of library desugaring.
    ignoreInvokes("not");
  }

  public static class PredicateBackportJava11Main {
    public static void main(String[] args) {
      testNot();
    }

    private static void testNot() {

      Predicate<Object> isNull = Objects::isNull;
      Predicate<Object> notNull = Predicate.not(isNull);

      assertEquals(notNull.test(null), false);
      assertEquals(notNull.test("something"), true);

      try {
        Predicate.not(null);
        throw new AssertionError("Expected to throw NPE");
      } catch (Throwable t) {
        // Expected.
      }
    }

    private static void assertEquals(Object expected, Object actual) {
      if (expected != actual && (expected == null || !expected.equals(actual))) {
        throw new AssertionError("Expected <" + expected + "> but was <" + actual + '>');
      }
    }
  }
}
