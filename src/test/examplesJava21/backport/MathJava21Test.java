// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
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
public final class MathJava21Test extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return getTestParameters()
        .withDexRuntimes()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK21)
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  public MathJava21Test(TestParameters parameters) {
    super(parameters, Math.class, Main.class);
    registerTarget(AndroidApiLevel.V, 92);
  }

  static final class Main extends MiniAssert {

    public static void main(String[] args) {
      testClampInt();
      testClampLong();
      testClampDouble();
      testClampFloat();

      testCeilDivIntInt();
      testCeilDivIntIntExact();
      testCeilDivLongLong();
      testCeilDivLongLongExact();
      testCeilDivLongInt();

      testCeilModIntInt();
      testCeilModLongLong();
      testCeilModLongInt();

      testDivideExactInt();
      testDivideExactLong();

      testFloorDivExactInt();
      testFloorDivExactLong();

      testUnsignedMultiplyHigh();
    }

    private static void testClampInt() {
      assertEquals(1, Math.clamp(1, 0, 5));
      assertEquals(0, Math.clamp(-1, 0, 5));
      assertEquals(5, Math.clamp(10, 0, 5));
      try {
        Math.clamp(1, 10, 5);
        fail("Should have thrown");
      } catch (IllegalArgumentException ignored) {
      }
    }

    private static void testClampLong() {
      assertEquals(1, Math.clamp(1L, 0L, 5L));
      assertEquals(0, Math.clamp(-1L, 0L, 5L));
      assertEquals(5, Math.clamp(10L, 0L, 5L));
      try {
        Math.clamp(1L, 10L, 5L);
        fail("Should have thrown");
      } catch (IllegalArgumentException ignored) {
      }
    }

    private static void testClampDouble() {
      assertEquals(1.0, Math.clamp(1.0, 0.0, 5.0));
      assertEquals(0.0, Math.clamp(-1.0, 0.0, 5.0));
      assertEquals(5.0, Math.clamp(10.0, 0.0, 5.0));
      try {
        Math.clamp(1.0, 10.0, 5.0);
        fail("Should have thrown");
      } catch (IllegalArgumentException ignored) {

      }
      // Check for -/+0.0.
      assertEquals(0.0, Math.clamp(-0.0, 0.0, 1.0));
      assertEquals(0.0, Math.clamp(0.0, -0.0, 1.0));
      assertEquals(-0.0, Math.clamp(-0.0, -1.0, 0.0));
      assertEquals(-0.0, Math.clamp(0.0, -1.0, -0.0));
      // Check for NaN.
      assertEquals(Double.NaN, Math.clamp(Double.NaN, 0.0, 1.0));
      try {
        Math.clamp(1.0, Double.NaN, 5.0);
        fail("Should have thrown");
      } catch (IllegalArgumentException ignored) {
      }
      try {
        Math.clamp(1.0, 10.0, Double.NaN);
        fail("Should have thrown");
      } catch (IllegalArgumentException ignored) {
      }
    }

    private static void testClampFloat() {
      assertEquals(1.0f, Math.clamp(1.0f, 0.0f, 5.0f));
      assertEquals(0.0f, Math.clamp(-1.0f, 0.0f, 5.0f));
      assertEquals(5.0f, Math.clamp(10.0f, 0.0f, 5.0f));
      try {
        Math.clamp(1.0f, 10.0f, 5.0f);
        fail("Should have thrown");
      } catch (IllegalArgumentException ignored) {

      }
      // Check for -/+0.0f.
      assertEquals(0.0f, Math.clamp(-0.0f, 0.0f, 1.0f));
      assertEquals(0.0f, Math.clamp(0.0f, -0.0f, 1.0f));
      assertEquals(-0.0f, Math.clamp(-0.0f, -1.0f, 0.0f));
      assertEquals(-0.0f, Math.clamp(0.0f, -1.0f, -0.0f));
      // Check for NaN.
      assertEquals(Float.NaN, Math.clamp(Float.NaN, 0.0f, 1.0f));
      try {
        Math.clamp(1.0f, Float.NaN, 5.0f);
        fail("Should have thrown");
      } catch (IllegalArgumentException ignored) {
      }
      try {
        Math.clamp(1.0f, 10.0f, Float.NaN);
        fail("Should have thrown");
      } catch (IllegalArgumentException ignored) {
      }
    }

    private static void testCeilDivIntInt() {
      assertEquals(1, Math.ceilDiv(7, 7));
      assertEquals(-1, Math.ceilDiv(-7, 7));
      assertEquals(1, Math.ceilDiv(3, 7));
      assertEquals(0, Math.ceilDiv(-3, 7));
      assertEquals(-2147483648, Math.ceilDiv(Integer.MIN_VALUE, -1));
    }

    private static void testCeilDivIntIntExact() {
      assertEquals(1, Math.ceilDivExact(7, 7));
      assertEquals(-1, Math.ceilDivExact(-7, 7));
      assertEquals(1, Math.ceilDivExact(3, 7));
      assertEquals(0, Math.ceilDivExact(-3, 7));
      try {
        Math.ceilDivExact(Integer.MIN_VALUE, -1);
        fail("Should have thrown");
      } catch (ArithmeticException ae) {

      }
    }

    private static void testCeilDivLongLong() {
      assertEquals(1L, Math.ceilDiv(7L, 7L));
      assertEquals(-1L, Math.ceilDiv(-7L, 7L));
      assertEquals(1L, Math.ceilDiv(3L, 7L));
      assertEquals(0, Math.ceilDiv(-3L, 7L));
      assertEquals(-9223372036854775808L, Math.ceilDiv(Long.MIN_VALUE, -1L));
    }

    private static void testCeilDivLongLongExact() {
      assertEquals(1L, Math.ceilDivExact(7L, 7L));
      assertEquals(-1L, Math.ceilDivExact(-7L, 7L));
      assertEquals(1L, Math.ceilDivExact(3L, 7L));
      assertEquals(0, Math.ceilDivExact(-3L, 7L));
      try {
        Math.ceilDivExact(Long.MIN_VALUE, -1L);
        fail("Should have thrown");
      } catch (ArithmeticException ae) {

      }
    }

    private static void testCeilDivLongInt() {
      assertEquals(1L, Math.ceilDiv(7L, 7));
      assertEquals(-1L, Math.ceilDiv(-7L, 7));
      assertEquals(1L, Math.ceilDiv(3L, 7));
      assertEquals(0, Math.ceilDiv(-3L, 7));
      assertEquals(-9223372036854775808L, Math.ceilDiv(Long.MIN_VALUE, -1));
    }

    private static void testCeilModIntInt() {
      assertEquals(0, Math.ceilMod(7, 7));
      assertEquals(0, Math.ceilMod(-7, 7));
      assertEquals(-4, Math.ceilMod(3, 7));
      assertEquals(-3, Math.ceilMod(-3, 7));
      assertEquals(0, Math.ceilMod(Integer.MIN_VALUE, -1));
    }

    private static void testCeilModLongLong() {
      assertEquals(0L, Math.ceilMod(7L, 7L));
      assertEquals(0L, Math.ceilMod(-7L, 7L));
      assertEquals(-4L, Math.ceilMod(3L, 7L));
      assertEquals(-3L, Math.ceilMod(-3L, 7L));
      assertEquals(0L, Math.ceilMod(Long.MIN_VALUE, -1L));
    }

    private static void testCeilModLongInt() {
      assertEquals(0L, Math.ceilMod(7L, 7));
      assertEquals(0L, Math.ceilMod(-7L, 7));
      assertEquals(-4L, Math.ceilMod(3L, 7));
      assertEquals(-3L, Math.ceilMod(-3L, 7));
      assertEquals(0L, Math.ceilMod(Long.MIN_VALUE, -1));
    }

    private static void testDivideExactInt() {
      assertEquals(1, Math.divideExact(7, 7));
      assertEquals(-1, Math.divideExact(-7, 7));
      assertEquals(0, Math.divideExact(3, 7));
      assertEquals(0, Math.divideExact(-3, 7));
      try {
        Math.divideExact(Integer.MIN_VALUE, -1);
        fail("Should have thrown");
      } catch (ArithmeticException ae) {

      }
    }

    private static void testDivideExactLong() {
      assertEquals(1L, Math.divideExact(7L, 7L));
      assertEquals(-1L, Math.divideExact(-7L, 7L));
      assertEquals(0, Math.divideExact(3L, 7L));
      assertEquals(0, Math.divideExact(-3L, 7L));
      try {
        Math.divideExact(Long.MIN_VALUE, -1L);
        fail("Should have thrown");
      } catch (ArithmeticException ae) {

      }
    }

    private static void testFloorDivExactInt() {
      assertEquals(1, Math.floorDivExact(7, 7));
      assertEquals(-1, Math.floorDivExact(-7, 7));
      assertEquals(0, Math.floorDivExact(3, 7));
      assertEquals(-1, Math.floorDivExact(-3, 7));
      try {
        Math.floorDivExact(Integer.MIN_VALUE, -1);
        fail("Should have thrown");
      } catch (ArithmeticException ae) {

      }
    }

    private static void testFloorDivExactLong() {
      assertEquals(1L, Math.floorDivExact(7L, 7L));
      assertEquals(-1L, Math.floorDivExact(-7L, 7L));
      assertEquals(0L, Math.floorDivExact(3L, 7L));
      assertEquals(-1L, Math.floorDivExact(-3L, 7L));
      try {
        Math.floorDivExact(Long.MIN_VALUE, -1L);
        fail("Should have thrown");
      } catch (ArithmeticException ae) {

      }
    }

    private static void testUnsignedMultiplyHigh() {
      assertEquals(
          31696182030193L, Math.unsignedMultiplyHigh(8222222222222222L, 71111111111111111L));
      assertEquals(
          71079414929080917L, Math.unsignedMultiplyHigh(-8222222222222222L, 71111111111111111L));
    }
  }
}
