// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.redundantarraygetelimination;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RedundantArrayGetEliminationWithThrowingArrayAccessTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines("-1", "-3", "-6", "-10", "42011", "9", "42010", "8", "13");

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  private void countInstanceGetOfField(int count, MethodSubject method, FieldSubject field) {
    assertThat(method, isPresentAndRenamed());
    assertThat(field, isPresentAndRenamed());
    assertEquals(
        count,
        method
            .streamInstructions()
            .filter(InstructionSubject::isInstancePut)
            .map(InstructionSubject::getField)
            .filter(f -> f.isIdenticalTo(field.getDexField()))
            .count());
  }

  private void zeroArrayGet(MethodSubject method) {
    countArrayGet(0, method);
  }

  private void oneArrayGet(MethodSubject method) {
    countArrayGet(1, method);
  }

  private void countArrayGet(int count, MethodSubject method) {
    assertThat(method, isPresentAndRenamed());
    assertEquals(count, method.streamInstructions().filter(InstructionSubject::isArrayGet).count());
  }

  private void oneInstanceGetOfField(MethodSubject method, FieldSubject field) {
    countInstanceGetOfField(1, method, field);
  }

  private void twoInstanceGetOfField(MethodSubject method, FieldSubject field) {
    countInstanceGetOfField(2, method, field);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject testClass = inspector.clazz(TestClass.class);
    assertThat(testClass, isPresentAndNotRenamed());
    FieldSubject iFld1 = testClass.uniqueFieldWithOriginalName("iFld1");
    twoInstanceGetOfField(
        testClass.uniqueMethodWithOriginalName("arrayOfKnownSizePutThrows"), iFld1);
    zeroArrayGet(testClass.uniqueMethodWithOriginalName("arrayOfKnownSizePutThrows"));
    twoInstanceGetOfField(
        testClass.uniqueMethodWithOriginalName("arrayOfKnownSizeGetThrows"), iFld1);
    oneArrayGet(testClass.uniqueMethodWithOriginalName("arrayOfKnownSizeGetThrows"));
    twoInstanceGetOfField(
        testClass.uniqueMethodWithOriginalName("arrayOfUnknownSizePutThrows"), iFld1);
    zeroArrayGet(testClass.uniqueMethodWithOriginalName("arrayOfUnknownSizePutThrows"));
    twoInstanceGetOfField(
        testClass.uniqueMethodWithOriginalName("arrayOfUnknownSizeGetThrows"), iFld1);
    oneArrayGet(testClass.uniqueMethodWithOriginalName("arrayOfUnknownSizeGetThrows"));
    oneInstanceGetOfField(
        testClass.uniqueMethodWithOriginalName("arrayOfKnownSizePutSucceeds"), iFld1);
    zeroArrayGet(testClass.uniqueMethodWithOriginalName("arrayOfKnownSizePutSucceeds"));
    oneInstanceGetOfField(
        testClass.uniqueMethodWithOriginalName("arrayOfKnownSizeGetSucceeds"), iFld1);
    zeroArrayGet(testClass.uniqueMethodWithOriginalName("arrayOfKnownSizeGetSucceeds"));
    twoInstanceGetOfField(
        testClass.uniqueMethodWithOriginalName("arrayOfUnknownSizePutSucceeds"), iFld1);
    zeroArrayGet(testClass.uniqueMethodWithOriginalName("arrayOfUnknownSizePutSucceeds"));
    twoInstanceGetOfField(
        testClass.uniqueMethodWithOriginalName("arrayOfUnknownSizeGetSucceeds"), iFld1);
    oneArrayGet(testClass.uniqueMethodWithOriginalName("arrayOfUnknownSizeGetSucceeds"));
    twoInstanceGetOfField(
        testClass.uniqueMethodWithOriginalName("arrayOfUnknownSizePutAndGetSucceeds"), iFld1);
    zeroArrayGet(testClass.uniqueMethodWithOriginalName("arrayOfUnknownSizePutAndGetSucceeds"));
  }

  @Test
  public void testD8Release() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .release()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT)
        .inspect(this::inspect);
  }

  static class TestClass {

    int iFld1;

    long[] array() {
      return new long[System.currentTimeMillis() > 0 ? 10 : 20];
    }

    @NeverInline
    void arrayOfKnownSizePutThrows() {
      int i5 = 61127, i7 = 42011;
      long[] lArr = new long[10];
      try {
        iFld1 -= 1L;
        lArr[i5] = i7;
        iFld1 = i7;
      } catch (ArrayIndexOutOfBoundsException err) {
      }
      System.out.println(iFld1);
    }

    @NeverInline
    void arrayOfKnownSizeGetThrows() {
      int i5 = 61127, i7 = 42011;
      long[] lArr = new long[10];
      try {
        iFld1 -= 2L;
        long x = lArr[i5];
        iFld1 = i7;
      } catch (ArrayIndexOutOfBoundsException err) {
      }
      System.out.println(iFld1);
    }

    @NeverInline
    void arrayOfUnknownSizePutThrows() {
      int i5 = 61127, i7 = 42011;
      long[] lArr = array();
      try {
        iFld1 -= 3L;
        lArr[i5] = i7;
        iFld1 = i7;
      } catch (ArrayIndexOutOfBoundsException err) {
      }
      System.out.println(iFld1);
    }

    @NeverInline
    void arrayOfUnknownSizeGetThrows() {
      int i5 = 61127, i7 = 42011;
      long[] lArr = array();
      try {
        iFld1 -= 4L;
        long x = lArr[i5];
        iFld1 = i7;
      } catch (ArrayIndexOutOfBoundsException err) {
      }
      System.out.println(iFld1);
    }

    @NeverInline
    void arrayOfKnownSizePutSucceeds() {
      int i5 = 9, i7 = 42011;
      long[] lArr = new long[10];
      try {
        iFld1 -= 5L;
        lArr[i5] = i7;
        iFld1 = i7;
      } catch (ArrayIndexOutOfBoundsException err) {
      }
      System.out.println(iFld1);
    }

    @NeverInline
    void arrayOfKnownSizeGetSucceeds() {
      int i5 = 9, i7 = 42011;
      long[] lArr = new long[10];
      try {
        iFld1 -= 6L;
        long x = lArr[i5];
        iFld1 = i5;
      } catch (ArrayIndexOutOfBoundsException err) {
      }
      System.out.println(iFld1);
    }

    @NeverInline
    void arrayOfUnknownSizePutSucceeds() {
      int i5 = 9, i7 = 42010;
      long[] lArr = array();
      try {
        iFld1 -= 225L;
        lArr[i5] = i7;
        iFld1 = i7;
      } catch (ArrayIndexOutOfBoundsException err) {
      }
      System.out.println(iFld1);
    }

    @NeverInline
    void arrayOfUnknownSizeGetSucceeds() {
      int i5 = 8, i7 = 42011;
      long[] lArr = array();
      try {
        iFld1 -= 225L;
        long x = lArr[i5];
        iFld1 = i5;
      } catch (ArrayIndexOutOfBoundsException err) {
      }
      System.out.println(iFld1);
    }

    @NeverInline
    void arrayOfUnknownSizePutAndGetSucceeds() {
      int i5 = 0, i7 = 13;
      long[] lArr = array();
      try {
        iFld1 -= 5L;
        lArr[i5] = i7;
        iFld1 = (int) lArr[i5];
      } catch (ArrayIndexOutOfBoundsException err) {
      }
      System.out.println(iFld1);
    }

    public static void main(String[] strArr) {
      TestClass test = new TestClass();
      test.arrayOfKnownSizePutThrows();
      test.arrayOfKnownSizeGetThrows();
      test.arrayOfUnknownSizePutThrows();
      test.arrayOfUnknownSizeGetThrows();
      test.arrayOfKnownSizePutSucceeds();
      test.arrayOfKnownSizeGetSucceeds();
      test.arrayOfUnknownSizePutSucceeds();
      test.arrayOfUnknownSizeGetSucceeds();
      test.arrayOfUnknownSizePutAndGetSucceeds();
    }
  }
}
