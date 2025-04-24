// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk17.records;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.JdkClassFileProvider;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.util.Objects;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RecordHashCodeManyFieldsTest extends TestBase {

  private static final String EXPECTED_RESULT =
      StringUtils.lines("true", "true", "false", "false", "true", "true");

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withAllRuntimes()
        .withAllApiLevelsAlsoForCf()
        .withPartialCompilation()
        .build();
  }

  private boolean isCfRuntimeWithNativeRecordSupport() {
    return parameters.isCfRuntime()
        && parameters.asCfRuntime().isNewerThanOrEqual(CfVm.JDK17)
        && parameters.getApiLevel().equals(AndroidApiLevel.B);
  }

  @Test
  public void testReference() throws Exception {
    assumeTrue(isCfRuntimeWithNativeRecordSupport());
    testForJvm(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .run(parameters.getRuntime(), TestClass.class, "no-desugar")
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .run(parameters.getRuntime(), TestClass.class, "desugar")
        .applyIf(
            isRecordsFullyDesugaredForD8(parameters)
                || runtimeWithRecordsSupport(parameters.getRuntime()),
            r -> r.assertSuccessWithOutput(EXPECTED_RESULT),
            r -> r.assertFailureWithErrorThatThrows(NoClassDefFoundError.class));
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    assumeTrue(parameters.isDexRuntime() || isCfRuntimeWithNativeRecordSupport());
    R8TestBuilder<?, ?, ?> builder =
        testForR8(parameters)
            .addInnerClassesAndStrippedOuter(getClass())
            .addInliningAnnotations()
            .addKeepMainRule(TestClass.class);
    if (parameters.isCfRuntime()) {
      builder
          .addLibraryProvider(JdkClassFileProvider.fromSystemJdk())
          .run(parameters.getRuntime(), TestClass.class, "no-desugar")
          .assertSuccessWithOutput(EXPECTED_RESULT);
      return;
    }
    builder
        .run(parameters.getRuntime(), TestClass.class, "desugar")
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testR8DontShrinkDontObfuscate() throws Exception {
    parameters.assumeR8TestParameters();
    assumeTrue(parameters.isDexRuntime() || isCfRuntimeWithNativeRecordSupport());
    R8TestBuilder<?, ?, ?> builder =
        testForR8(parameters)
            .addInnerClassesAndStrippedOuter(getClass())
            .addDontShrink()
            .addDontObfuscate()
            .addInliningAnnotations()
            .addKeepMainRule(TestClass.class);
    if (parameters.isCfRuntime()) {
      builder
          .addLibraryProvider(JdkClassFileProvider.fromSystemJdk())
          .run(parameters.getRuntime(), TestClass.class, "no-desugar")
          .assertSuccessWithOutput(EXPECTED_RESULT);
      return;
    }
    builder
        .run(parameters.getRuntime(), TestClass.class, "desugar")
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  public static class TestClass {

    record Wide(int i1, int i2, long l1, long l2, double d1, double d2, String s1, String s2) {}

    // It needs at least 33 fields with all primitive types.
    record Many(
        String s1,
        String s2,
        String s3,
        String s4,
        String s5,
        boolean b1,
        boolean b2,
        boolean b3,
        boolean b4,
        boolean b5,
        byte by1,
        byte by2,
        byte by3,
        byte by4,
        byte by5,
        short sh1,
        short sh2,
        short sh3,
        short sh4,
        char c1,
        char c2,
        char c3,
        char c4,
        int i1,
        int i2,
        int i3,
        int i4,
        int i5,
        long l1,
        long l2,
        long l3,
        long l4,
        long l5,
        float f1,
        float f2,
        float f3,
        float f4,
        float f5,
        double d1,
        double d2,
        double d3,
        double d4,
        double d5) {}

    public static void main(String[] args) {

      Many m1 =
          new Many(
              "s1", "s2", null, "s4", "s5", true, false, true, false, true, (byte) 1, (byte) 2,
              (byte) 3, (byte) 4, (byte) 5, (short) 6, (short) 7, (short) 8, (short) 9, 'a', 'b',
              'c', 'd', 21, 22, 23, 24, 25, 31L, 32L, 33L, 34L, 35L, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f,
              11.0, 12.0, 13.0, 14.0, 15.0);
      Many m2 =
          new Many(
              "ss1", "ss2", null, "s4", null, true, true, true, false, true, (byte) 1, (byte) 2,
              (byte) 3, (byte) 4, (byte) 5, (short) 6, (short) 7, (short) 8, (short) 9, 'a', 'b',
              'f', 'd', 21, 22, 23, 24, 25, 31L, 32L, 33L, 34L, 35L, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f,
              11.0, 12.0, 13.0, 14.0, 15.0);
      System.out.println(equals(m1.hashCode(), m1.hashCode()));
      System.out.println(equalsHash(m1, m1));
      System.out.println(equals(m1.hashCode(), m2.hashCode()));
      System.out.println(equalsHash(m1, m2));
      // The equals below are valid only if the record is desugared, i.e., not on r8 cf to cf.
      if (args[0].equals("desugar")) {
        System.out.println(equals(m1.hashCode(), hash(m1)));
        Wide w = new Wide(1, 2, 3L, 4L, 1.0, 2.0, "s1", null);
        System.out.println(equals(w.hashCode(), hash(w)));
      } else {
        System.out.println("true");
        System.out.println("true");
      }
    }

    @NeverInline
    public static int hash(Many m) {
      int hash = Boolean.hashCode(m.b1);
      hash = 31 * hash + Boolean.hashCode(m.b2);
      hash = 31 * hash + Boolean.hashCode(m.b3);
      hash = 31 * hash + Boolean.hashCode(m.b4);
      hash = 31 * hash + Boolean.hashCode(m.b5);
      hash = 31 * hash + Integer.hashCode(m.by1);
      hash = 31 * hash + Integer.hashCode(m.by2);
      hash = 31 * hash + Integer.hashCode(m.by3);
      hash = 31 * hash + Integer.hashCode(m.by4);
      hash = 31 * hash + Integer.hashCode(m.by5);
      hash = 31 * hash + Integer.hashCode(m.sh1);
      hash = 31 * hash + Integer.hashCode(m.sh2);
      hash = 31 * hash + Integer.hashCode(m.sh3);
      hash = 31 * hash + Integer.hashCode(m.sh4);
      hash = 31 * hash + Integer.hashCode(m.c1);
      hash = 31 * hash + Integer.hashCode(m.c2);
      hash = 31 * hash + Integer.hashCode(m.c3);
      hash = 31 * hash + Integer.hashCode(m.c4);
      hash = 31 * hash + Integer.hashCode(m.i1);
      hash = 31 * hash + Integer.hashCode(m.i2);
      hash = 31 * hash + Integer.hashCode(m.i3);
      hash = 31 * hash + Integer.hashCode(m.i4);
      hash = 31 * hash + Integer.hashCode(m.i5);
      hash = 31 * hash + Long.hashCode(m.l1);
      hash = 31 * hash + Long.hashCode(m.l2);
      hash = 31 * hash + Long.hashCode(m.l3);
      hash = 31 * hash + Long.hashCode(m.l4);
      hash = 31 * hash + Long.hashCode(m.l5);
      hash = 31 * hash + Float.hashCode(m.f1);
      hash = 31 * hash + Float.hashCode(m.f2);
      hash = 31 * hash + Float.hashCode(m.f3);
      hash = 31 * hash + Float.hashCode(m.f4);
      hash = 31 * hash + Float.hashCode(m.f5);
      hash = 31 * hash + Double.hashCode(m.d1);
      hash = 31 * hash + Double.hashCode(m.d2);
      hash = 31 * hash + Double.hashCode(m.d3);
      hash = 31 * hash + Double.hashCode(m.d4);
      hash = 31 * hash + Double.hashCode(m.d5);
      hash = 31 * hash + Objects.hashCode(m.s1);
      hash = 31 * hash + Objects.hashCode(m.s2);
      hash = 31 * hash + Objects.hashCode(m.s3);
      hash = 31 * hash + Objects.hashCode(m.s4);
      hash = 31 * hash + Objects.hashCode(m.s5);
      return hash;
    }

    @NeverInline
    public static int hash(Wide w) {
      int hash = w.i1;
      hash = 31 * hash + w.i2;
      hash = 31 * hash + Long.hashCode(w.l1);
      hash = 31 * hash + Long.hashCode(w.l2);
      hash = 31 * hash + Double.hashCode(w.d1);
      hash = 31 * hash + Double.hashCode(w.d2);
      hash = 31 * hash + Objects.hashCode(w.s1);
      hash = 31 * hash + Objects.hashCode(w.s2);
      return hash;
    }

    @NeverInline
    public static boolean equals(int i1, int i2) {
      return System.currentTimeMillis() > 0 ? i1 == i2 : false;
    }

    @NeverInline
    public static boolean equalsHash(Record r1, Record r2) {
      return System.currentTimeMillis() > 0 ? equals(r1.hashCode(), r2.hashCode()) : false;
    }
  }
}
