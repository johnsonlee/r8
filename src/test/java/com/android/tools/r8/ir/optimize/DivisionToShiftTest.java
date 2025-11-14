// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeMatchers;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
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

  @Parameterized.Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private boolean isIntegerDivideUnsignedSupported() {
    return parameters.isCfRuntime()
        || parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.O);
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addProgramClasses(Main.class)
        .addKeepMainRule(Main.class)
        .compile()
        .inspect(
            inspector -> {
              MethodSubject mainMethod = inspector.clazz(Main.class).mainMethod();
              if (isIntegerDivideUnsignedSupported()) {
                assertEquals(31, divideUnsignedCallCount(mainMethod));
              } else {
                // `java.lang.Integer::divideUnsigned` is backported and then inlined
                long divisionCount =
                    mainMethod.streamInstructions().filter(InstructionSubject::isDivision).count();
                assertEquals(31, divisionCount);
              }
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters)
        .addProgramClasses(Main.class)
        .collectSyntheticItems()
        .compile()
        .inspectWithSyntheticItems(
            (inspector, syntheticItemsTestUtils) -> {
              MethodSubject mainMethod = inspector.clazz(Main.class).mainMethod();
              if (isIntegerDivideUnsignedSupported()) {
                assertEquals(31, divideUnsignedCallCount(mainMethod));
              } else {
                MethodReference backportMethod =
                    syntheticItemsTestUtils.syntheticBackportMethod(
                        Main.class,
                        0,
                        Integer.class.getMethod("divideUnsigned", int.class, int.class));
                long backportCallCount =
                    mainMethod
                        .streamInstructions()
                        .filter(CodeMatchers.isInvokeWithTarget(backportMethod))
                        .count();
                assertEquals(31, backportCallCount);
              }
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
    ;
  }

  private static long divideUnsignedCallCount(MethodSubject method) {
    return method
        .streamInstructions()
        .filter(CodeMatchers.isInvokeWithTarget("java.lang.Integer", "divideUnsigned"))
        .count();
  }

  public static class Main {
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
}
