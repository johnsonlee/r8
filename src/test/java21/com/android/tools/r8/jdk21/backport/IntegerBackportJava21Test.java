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
public final class IntegerBackportJava21Test extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return TestBase.getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK21)
        .withDexRuntimes()
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  public IntegerBackportJava21Test(TestParameters parameters) {
    super(parameters, Integer.class, IntegerBackportJava21Main.class);

    registerTarget(AndroidApiLevel.BAKLAVA, 2);
  }

  public static class IntegerBackportJava21Main {
    private static final int[] INTERESTING_VALUES = {
      Integer.MIN_VALUE,
      Integer.MAX_VALUE,
      Short.MIN_VALUE,
      Short.MAX_VALUE,
      Byte.MIN_VALUE,
      Byte.MAX_VALUE,
      0,
      -1,
      1,
      -42,
      42
    };

    private static final int[] INTERESTING_MASKS = {
      0, -1, 0xF000F000, 0xFF00FF00, 0xFFF0FFF0, 0xFFFF0FF0, 0xFFFFF000, 0xFFFFFF00, 0xFFFFFFF0,
    };

    public static void main(String[] args) {
      for (int interestingValue : INTERESTING_VALUES) {
        for (int interestingMask : INTERESTING_MASKS) {
          testCompress(interestingValue, interestingMask);
          testExpand(interestingValue, interestingMask);
        }
      }
    }

    static void testCompress(int i, int mask) {
      assertEquals(
          "compress " + i + " mask " + mask, bookCompress(i, mask), Integer.compress(i, mask));
    }

    static int bookCompress(int i, int mask) {
      int result = 0;
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

    static void testExpand(int i, int mask) {
      assertEquals("expand " + i + " mask " + mask, bookExpand(i, mask), Integer.expand(i, mask));
    }

    static int bookExpand(int i, int mask) {
      int result = 0;
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

    private static void assertEquals(String m, int expected, int actual) {
      if (expected != actual) {
        throw new AssertionError(m + " Expected <" + expected + "> but was <" + actual + '>');
      }
    }
  }
}
