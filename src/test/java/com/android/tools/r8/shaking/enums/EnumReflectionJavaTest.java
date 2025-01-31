// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.enums;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.shaking.enums.EnumReflectionJavaTest.Helpers.EnumA;
import com.android.tools.r8.shaking.enums.EnumReflectionJavaTest.Helpers.EnumB;
import com.android.tools.r8.shaking.enums.EnumReflectionJavaTest.Helpers.EnumC;
import com.android.tools.r8.shaking.enums.EnumReflectionJavaTest.Helpers.EnumD;
import com.android.tools.r8.shaking.enums.EnumReflectionJavaTest.Helpers.EnumE;
import com.android.tools.r8.shaking.enums.EnumReflectionJavaTest.Helpers.EnumF;
import com.android.tools.r8.shaking.enums.EnumReflectionJavaTest.Helpers.EnumG;
import com.android.tools.r8.shaking.enums.EnumReflectionJavaTest.Helpers.EnumH;
import com.android.tools.r8.shaking.enums.EnumReflectionJavaTest.Helpers.EnumI;
import com.android.tools.r8.shaking.enums.EnumReflectionJavaTest.Helpers.EnumJ;
import com.android.tools.r8.shaking.enums.EnumReflectionJavaTest.Helpers.EnumK;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Tests for the non-Android-specific APIs that cause an Enum type's values() method to be kept. */
@RunWith(Parameterized.class)
public class EnumReflectionJavaTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withMaximumApiLevel().build();
  }

  private static final List<String> EXPECTED_OUTPUT =
      Arrays.asList(
          "none: [A, B]",
          "all: [B, C]",
          "of: [C]",
          "of: [D, E]",
          "of: [E, F, G]",
          "of: [F, G, H, I]",
          "of: [G, H, I, J, K]",
          "of: [H, I, J, K, L, M]",
          "range: [I, J]",
          "map: {J=1}",
          "valueOf: K",
          "phi: [B]");

  public static class Helpers {

    public enum EnumA {
      A,
      B
    }

    public enum EnumB {
      B,
      C
    }

    public enum EnumC {
      C {}, // Test anonymous subtype.
      D
    }

    public enum EnumD {
      D,
      E
    }

    public enum EnumE {
      E,
      F,
      G
    }

    public enum EnumF {
      F,
      G,
      H,
      I
    }

    public enum EnumG {
      G,
      H,
      I,
      J,
      K
    }

    public enum EnumH {
      H,
      I,
      J,
      K,
      L,
      M
    }

    public enum EnumI {
      I,
      J
    }

    public enum EnumJ {
      J,
      K
    }

    public enum EnumK {
      K,
      L
    }
  }

  public static class TestMain {
    @NeverInline
    private static void noneOf() {
      System.out.println("none: " + EnumSet.complementOf(EnumSet.noneOf(EnumA.class)));
    }

    @NeverInline
    private static void allOf() {
      System.out.println("all: " + EnumSet.allOf(EnumB.class));
    }

    @NeverInline
    private static void of1() {
      System.out.println("of: " + EnumSet.of(EnumC.C));
    }

    @NeverInline
    private static void of2() {
      System.out.println("of: " + EnumSet.of(EnumD.D, EnumD.E));
    }

    @NeverInline
    private static void of3() {
      System.out.println("of: " + EnumSet.of(EnumE.E, EnumE.F, EnumE.G));
    }

    @NeverInline
    private static void of4() {
      System.out.println("of: " + EnumSet.of(EnumF.F, EnumF.G, EnumF.H, EnumF.I));
    }

    @NeverInline
    private static void of5() {
      System.out.println("of: " + EnumSet.of(EnumG.G, EnumG.H, EnumG.I, EnumG.J, EnumG.K));
    }

    @NeverInline
    private static void ofVarArgs() {
      System.out.println("of: " + EnumSet.of(EnumH.H, EnumH.I, EnumH.J, EnumH.K, EnumH.L, EnumH.M));
    }

    @NeverInline
    private static void range() {
      System.out.println("range: " + EnumSet.range(EnumI.I, EnumI.J));
    }

    @NeverInline
    private static void map() {
      EnumMap<EnumJ, Integer> map = new EnumMap<>(EnumJ.class);
      map.put(EnumJ.J, 1);
      System.out.println("map: " + map);
    }

    @NeverInline
    private static void valueOf() {
      System.out.println("valueOf: " + EnumK.valueOf("K"));
    }

    public static void main(String[] args) {
      // Use different methods to ensure Enqueuer.traceInvokeStatic() triggers for each one.
      noneOf();
      allOf();
      of1();
      of2();
      of3();
      of4();
      of5();
      ofVarArgs();
      range();
      map();
      valueOf();
      // Ensure phi as argument does not cause issues.
      System.out.println(
          "phi: " + EnumSet.of((Enum) (args.length > 10 ? (Object) EnumA.A : (Object) EnumB.B)));
    }
  }

  // EnumC.C references this class in its constructor.
  private static final String ENUM_SUBTYPE_BRIDGE_CLASS_NAME =
      EnumReflectionJavaTest.class.getName() + "$1";

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClassesAndInnerClasses(Helpers.class)
        .addProgramClasses(TestMain.class)
        .addProgramClasses(Class.forName(ENUM_SUBTYPE_BRIDGE_CLASS_NAME))
        .run(parameters.getRuntime(), TestMain.class)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClassesAndInnerClasses(Helpers.class)
        .addProgramClasses(TestMain.class)
        .addProgramClasses(Class.forName(ENUM_SUBTYPE_BRIDGE_CLASS_NAME))
        .enableInliningAnnotations()
        .addKeepMainRule(TestMain.class)
        .compile()
        .run(parameters.getRuntime(), TestMain.class)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }
}
