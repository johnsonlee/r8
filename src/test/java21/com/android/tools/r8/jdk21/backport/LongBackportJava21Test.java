// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk21.backport;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.desugar.backports.AbstractBackportTest;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class LongBackportJava21Test extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return TestBase.getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK21)
        .withDexRuntimes()
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  public LongBackportJava21Test(TestParameters parameters) {
    super(parameters, Long.class, LongBackportJava21Main.class);

    registerTarget(AndroidApiLevel.BAKLAVA, 2);
  }

  public static class LongBackportJava21Main {
    private static final long[] INTERESTING_VALUES = {
      Long.MIN_VALUE,
      Long.MAX_VALUE,
      Integer.MIN_VALUE,
      Integer.MAX_VALUE,
      Short.MIN_VALUE,
      Short.MAX_VALUE,
      Byte.MIN_VALUE,
      Byte.MAX_VALUE,
      0L,
      -1L,
      1L,
      -42L,
      42L
    };

    private static final long[] INTERESTING_MASKS = {
      0L,
      -1L,
      0xF000F000F0L,
      0xFF00FF00FFL,
      0xFFF0FFF000L,
      0xFFFF0FFFF0L,
      0xFFFFF00000L,
      0xFFFFFF0000L,
      0xFFFFFFF000L,
      0xFFFFFFFF00L,
      0xFFFFFFFFF0L,
      0xFFFFFFFFFFL,
    };

    public static void main(String[] args) {
      for (long interestingValue : INTERESTING_VALUES) {
        for (long interestingMask : INTERESTING_MASKS) {
          testCompress(interestingValue, interestingMask);
          testExpand(interestingValue, interestingMask);
        }
      }
    }

    static void testCompress(long i, long mask) {
      assertEquals(
          "compress " + i + " mask " + mask, bookCompress(i, mask), Long.compress(i, mask));
    }

    static long bookCompress(long i, long mask) {
      long result = 0;
      int rpos = 0;
      while (mask != 0) {
        if ((mask & 1) != 0) {
          result |= (i & 1) << rpos++;
        }
        i >>>= 1;
        mask >>>= 1;
      }
      return result;
    }

    static void testExpand(long i, long mask) {
      assertEquals("expand " + i + " mask " + mask, bookExpand(i, mask), Long.expand(i, mask));
    }

    static long bookExpand(long i, long mask) {
      long result = 0;
      int rpos = 0;
      while (mask != 0) {
        if ((mask & 1) != 0) {
          result |= (i & 1) << rpos;
          i >>>= 1;
        }
        rpos++;
        mask >>>= 1;
      }
      return result;
    }

    private static void assertEquals(String m, long expected, long actual) {
      if (expected != actual) {
        throw new AssertionError(m + " Expected <" + expected + "> but was <" + actual + '>');
      }
    }
  }
}
