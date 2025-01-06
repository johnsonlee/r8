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
    registerTarget(AndroidApiLevel.V, 30);
  }

  static final class Main extends MiniAssert {

    public static void main(String[] args) {
      testClampInt();
      testClampLong();
      testClampDouble();
      testClampFloat();
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
  }
}
