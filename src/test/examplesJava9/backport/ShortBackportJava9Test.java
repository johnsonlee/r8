// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package backport;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.desugar.backports.AbstractBackportTest;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class ShortBackportJava9Test extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK9)
        .withDexRuntimes()
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  public ShortBackportJava9Test(TestParameters parameters) {
    super(parameters, Short.class, ShortBackportJava9Main.class);

    // Short.compareUnsigned added in API 31.
    registerTarget(AndroidApiLevel.S, 16);
  }

  public static class ShortBackportJava9Main {
    private static final byte MIN_UNSIGNED_VALUE = (short) 0;
    private static final byte MAX_UNSIGNED_VALUE = (short) -1;

    public static void main(String[] args) {
      testCompareUnsigned();
    }

    private static void testCompareUnsigned() {
      assertTrue(Short.compareUnsigned(MIN_UNSIGNED_VALUE, MIN_UNSIGNED_VALUE) == 0);
      assertTrue(Short.compareUnsigned(MIN_UNSIGNED_VALUE, Short.MAX_VALUE) < 0);
      assertTrue(Short.compareUnsigned(MIN_UNSIGNED_VALUE, Short.MIN_VALUE) < 0);
      assertTrue(Short.compareUnsigned(MIN_UNSIGNED_VALUE, MAX_UNSIGNED_VALUE) < 0);

      assertTrue(Short.compareUnsigned(Short.MAX_VALUE, MIN_UNSIGNED_VALUE) > 0);
      assertTrue(Short.compareUnsigned(Short.MAX_VALUE, Short.MAX_VALUE) == 0);
      assertTrue(Short.compareUnsigned(Short.MAX_VALUE, Short.MIN_VALUE) < 0);
      assertTrue(Short.compareUnsigned(Short.MAX_VALUE, MAX_UNSIGNED_VALUE) < 0);

      assertTrue(Short.compareUnsigned(Short.MIN_VALUE, MIN_UNSIGNED_VALUE) > 0);
      assertTrue(Short.compareUnsigned(Short.MIN_VALUE, Short.MAX_VALUE) > 0);
      assertTrue(Short.compareUnsigned(Short.MIN_VALUE, Short.MIN_VALUE) == 0);
      assertTrue(Short.compareUnsigned(Short.MIN_VALUE, MAX_UNSIGNED_VALUE) < 0);

      assertTrue(Short.compareUnsigned(MAX_UNSIGNED_VALUE, MIN_UNSIGNED_VALUE) > 0);
      assertTrue(Short.compareUnsigned(MAX_UNSIGNED_VALUE, Short.MAX_VALUE) > 0);
      assertTrue(Short.compareUnsigned(MAX_UNSIGNED_VALUE, Short.MIN_VALUE) > 0);
      assertTrue(Short.compareUnsigned(MAX_UNSIGNED_VALUE, MAX_UNSIGNED_VALUE) == 0);
    }

    private static void assertTrue(boolean value) {
      if (!value) {
        throw new AssertionError("Expected <true> but was <false>");
      }
    }
  }
}
