// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeMatchers;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DivisionToShiftTest extends TestBase {

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines(
          "61728394",
          "30864197",
          "15432098",
          "7716049",
          "3858024",
          "1929012",
          "964506",
          "482253",
          "241126",
          "120563",
          "60281",
          "30140",
          "15070",
          "7535",
          "3767",
          "1883",
          "941",
          "470",
          "235",
          "117",
          "58",
          "29",
          "14",
          "7",
          "3",
          "1",
          "0",
          "0",
          "0",
          "0",
          "0");

  @Parameterized.Parameter(0)
  public TestParameters parameters;

  @Parameterized.Parameter(1)
  public CompilationMode mode;

  @Parameters(name = "{0}, {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), CompilationMode.values());
  }

  private boolean isIntegerDivideUnsignedSupported() {
    return parameters.isCfRuntime()
        || parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.O);
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(PositiveTest.class)
        .run(parameters.getRuntime(), PositiveTest.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8Positive() throws Exception {
    testForR8(parameters)
        .addProgramClasses(PositiveTest.class)
        .setMode(mode)
        .addKeepMainRule(PositiveTest.class)
        .collectSyntheticItems()
        .compile()
        .inspectWithSyntheticItems(
            (inspector, syntheticItemsTestUtils) -> {
              MethodSubject mainMethod = inspector.clazz(PositiveTest.class).mainMethod();
              if (isIntegerDivideUnsignedSupported()) {
                boolean isOptimizationEnabled = mode.isRelease();
                if (isOptimizationEnabled) {
                  assertEquals(
                      DIVISION_COUNT,
                      mainMethod
                          .streamInstructions()
                          .filter(InstructionSubject::isUnsignedShiftRight)
                          .count());
                } else {
                  assertEquals(DIVISION_COUNT, divideUnsignedCallCount(mainMethod));
                }
              } else {
                boolean isBackportInlined = mode.isRelease();
                if (isBackportInlined) {
                  long divisionCount =
                      mainMethod
                          .streamInstructions()
                          .filter(InstructionSubject::isDivision)
                          .count();
                  assertEquals(DIVISION_COUNT, divisionCount);
                } else {
                  long backportCallCount = backportCallCount(syntheticItemsTestUtils, mainMethod);
                  assertEquals(DIVISION_COUNT, backportCallCount);
                }
              }
            })
        .run(parameters.getRuntime(), PositiveTest.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testD8Positive() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters)
        .addProgramClasses(PositiveTest.class)
        .setMode(mode)
        .collectSyntheticItems()
        .compile()
        .inspectWithSyntheticItems(
            (inspector, syntheticItemsTestUtils) -> {
              MethodSubject mainMethod = inspector.clazz(PositiveTest.class).mainMethod();
              if (isIntegerDivideUnsignedSupported()) {
                boolean isOptimizationEnabled = mode.isRelease();
                if (isOptimizationEnabled) {
                  long unsignedShiftCount =
                      mainMethod
                          .streamInstructions()
                          .filter(InstructionSubject::isUnsignedShiftRight)
                          .count();
                  assertEquals(DIVISION_COUNT, unsignedShiftCount);
                } else {
                  assertEquals(DIVISION_COUNT, divideUnsignedCallCount(mainMethod));
                }
              } else {
                assertEquals(
                    DIVISION_COUNT, backportCallCount(syntheticItemsTestUtils, mainMethod));
              }
            })
        .run(parameters.getRuntime(), PositiveTest.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  private static long backportCallCount(
      SyntheticItemsTestUtils syntheticItemsTestUtils, MethodSubject mainMethod)
      throws NoSuchMethodException {
    MethodReference backportMethod =
        syntheticItemsTestUtils.syntheticBackportMethod(
            PositiveTest.class, 0, Integer.class.getMethod("divideUnsigned", int.class, int.class));
    return mainMethod
        .streamInstructions()
        .filter(CodeMatchers.isInvokeWithTarget(backportMethod))
        .count();
  }

  private static long divideUnsignedCallCount(MethodSubject method) {
    return method
        .streamInstructions()
        .filter(CodeMatchers.isInvokeWithTarget("java.lang.Integer", "divideUnsigned"))
        .count();
  }

  private static final int DIVISION_COUNT = 31;

  public static class PositiveTest {
    public static void main(String[] args) {
      System.out.println(Integer.divideUnsigned(123456789, 0b00000000000000000000000000000010));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000000000000000000000000000100));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000000000000000000000000001000));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000000000000000000000000010000));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000000000000000000000000100000));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000000000000000000000001000000));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000000000000000000000010000000));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000000000000000000000100000000));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000000000000000000001000000000));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000000000000000000010000000000));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000000000000000000100000000000));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000000000000000001000000000000));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000000000000000010000000000000));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000000000000000100000000000000));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000000000000001000000000000000));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000000000000010000000000000000));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000000000000100000000000000000));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000000000001000000000000000000));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000000000010000000000000000000));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000000000100000000000000000000));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000000001000000000000000000000));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000000010000000000000000000000));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000000100000000000000000000000));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000001000000000000000000000000));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000010000000000000000000000000));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000100000000000000000000000000));
      System.out.println(Integer.divideUnsigned(123456789, 0b00001000000000000000000000000000));
      System.out.println(Integer.divideUnsigned(123456789, 0b00010000000000000000000000000000));
      System.out.println(Integer.divideUnsigned(123456789, 0b00100000000000000000000000000000));
      System.out.println(Integer.divideUnsigned(123456789, 0b01000000000000000000000000000000));
      System.out.println(Integer.divideUnsigned(123456789, 0b10000000000000000000000000000000));
    }
  }

  @Test
  public void testR8Negative() throws Exception {
    testForR8(parameters)
        .addProgramClasses(NegativeTest.class)
        .setMode(mode)
        .addKeepMainRule(NegativeTest.class)
        .compile()
        .inspect(
            inspector -> {
              MethodSubject mainMethod = inspector.clazz(NegativeTest.class).mainMethod();
              assertFalse(
                  mainMethod
                      .streamInstructions()
                      .anyMatch(InstructionSubject::isUnsignedShiftRight));
            });
  }

  @Test
  public void testD8Negative() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters)
        .addProgramClasses(NegativeTest.class)
        .setMode(mode)
        .compile()
        .inspect(
            inspector -> {
              MethodSubject mainMethod = inspector.clazz(NegativeTest.class).mainMethod();
              assertFalse(
                  mainMethod
                      .streamInstructions()
                      .anyMatch(InstructionSubject::isUnsignedShiftRight));
            });
  }

  public static class NegativeTest {
    public static void main(String[] args) {
      System.out.println(Integer.divideUnsigned(123456789, 0b00000000000000000000000000000001));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000000000000000000000000000011));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000000000000000000000000000101));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000000000000000000000000001010));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000000000000000000000000010100));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000000000000000000000000100010));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000000000000000000000001000001));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000000000000000000000010001000));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000000000000000000000100100000));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000000000000000000001000000100));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000000000000000000010000100000));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000000000000000000100100000000));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000000000000000001000000000010));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000000000000000010000001000000));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000000000000000100010000000000));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000000000000001000000001000000));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000000000000010000000000010000));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000000000000100010000000000000));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000000000001000000000010000000));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000000000010000000100000000000));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000000000100000000000001000000));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000000001000000001000000000000));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000000011111111111111111111111));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000000100000100000000000000000));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000001000000000100000000000000));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000010000000000000000100000000));
      System.out.println(Integer.divideUnsigned(123456789, 0b00000100000000000000100000000000));
      System.out.println(Integer.divideUnsigned(123456789, 0b00001000000011100000000100000000));
      System.out.println(Integer.divideUnsigned(123456789, 0b00010000000000000000000010000000));
      System.out.println(Integer.divideUnsigned(123456789, 0b00100000000000000000000000000001));
      System.out.println(Integer.divideUnsigned(123456789, 0b01000000000000000000000000001111));
      System.out.println(Integer.divideUnsigned(123456789, 0b11111111111111111111111111111111));
    }
  }
}
