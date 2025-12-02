// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeMatchers;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DivisionToShiftLongTest extends TestBase {

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines(
          "6172839455055606",
          "3086419727527803",
          "1543209863763901",
          "771604931881950",
          "385802465940975",
          "192901232970487",
          "96450616485243",
          "48225308242621",
          "24112654121310",
          "12056327060655",
          "6028163530327",
          "3014081765163",
          "1507040882581",
          "753520441290",
          "376760220645",
          "188380110322",
          "94190055161",
          "47095027580",
          "23547513790",
          "11773756895",
          "5886878447",
          "2943439223",
          "1471719611",
          "735859805",
          "367929902",
          "183964951",
          "91982475",
          "45991237",
          "22995618",
          "11497809",
          "5748904",
          "2874452",
          "1437226",
          "718613",
          "359306",
          "179653",
          "89826",
          "44913",
          "22456",
          "11228",
          "5614",
          "2807",
          "1403",
          "701",
          "350",
          "175",
          "87",
          "43",
          "21",
          "10",
          "5",
          "2",
          "1",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0");

  private static int getExpectedDivisionCount(
      boolean isDivideUnsignedSupported, boolean isBackportInlined) {
    if (!isDivideUnsignedSupported && isBackportInlined) {
      // Each backport has two divisions.
      // The last inlined backport is folded into a constant.
      return 63 * 2 - 2;
    } else {
      return 63;
    }
  }

  @Parameterized.Parameter(0)
  public TestParameters parameters;

  @Parameterized.Parameter(1)
  public CompilationMode mode;

  @Parameters(name = "{0}, {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), CompilationMode.values());
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
        .enableMemberValuePropagationAnnotations()
        .enableInliningAnnotations()
        .addKeepMainRule(PositiveTest.class)
        .collectSyntheticItems()
        .compile()
        .inspectWithSyntheticItems(
            (inspector, syntheticItemsTestUtils) -> {
              MethodSubject mainMethod = inspector.clazz(PositiveTest.class).mainMethod();
              boolean isBackportInlined = mode.isRelease();
              int expectedDivisionCount =
                  getExpectedDivisionCount(
                      parameters.canUseJavaLangDivideUnsigned(), isBackportInlined);
              if (parameters.canUseJavaLangDivideUnsigned()) {
                boolean isOptimizationEnabled = mode.isRelease();
                if (isOptimizationEnabled) {
                  assertEquals(
                      expectedDivisionCount,
                      mainMethod
                          .streamInstructions()
                          .filter(InstructionSubject::isUnsignedShiftRight)
                          .count());
                } else {
                  assertEquals(expectedDivisionCount, divideUnsignedCallCount(mainMethod));
                }
              } else {
                if (isBackportInlined) {
                  long divisionCount =
                      mainMethod
                          .streamInstructions()
                          .filter(InstructionSubject::isDivision)
                          .count();
                  assertEquals(expectedDivisionCount, divisionCount);
                } else {
                  long backportCallCount = backportCallCount(syntheticItemsTestUtils, mainMethod);
                  assertEquals(expectedDivisionCount, backportCallCount);
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
              int expectedDivisionCount =
                  getExpectedDivisionCount(parameters.canUseJavaLangDivideUnsigned(), false);
              if (parameters.canUseJavaLangDivideUnsigned()) {
                boolean isOptimizationEnabled = mode.isRelease();
                if (isOptimizationEnabled) {
                  long unsignedShiftCount =
                      mainMethod
                          .streamInstructions()
                          .filter(InstructionSubject::isUnsignedShiftRight)
                          .count();
                  assertEquals(expectedDivisionCount, unsignedShiftCount);
                } else {
                  assertEquals(expectedDivisionCount, divideUnsignedCallCount(mainMethod));
                }
              } else {
                assertEquals(
                    expectedDivisionCount, backportCallCount(syntheticItemsTestUtils, mainMethod));
              }
            })
        .run(parameters.getRuntime(), PositiveTest.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  private static long backportCallCount(
      SyntheticItemsTestUtils syntheticItemsTestUtils, MethodSubject mainMethod)
      throws NoSuchMethodException {
    Method baseDivisionMethod = Long.class.getMethod("divideUnsigned", long.class, long.class);
    MethodReference backportMethod =
        syntheticItemsTestUtils.syntheticBackportMethod(PositiveTest.class, 0, baseDivisionMethod);
    return mainMethod
        .streamInstructions()
        .filter(CodeMatchers.isInvokeWithTarget(backportMethod))
        .count();
  }

  private static long divideUnsignedCallCount(MethodSubject method) {
    return method
        .streamInstructions()
        .filter(CodeMatchers.isInvokeWithTarget("java.lang.Long", "divideUnsigned"))
        .count();
  }

  public static class PositiveTest {
    public static void main(String[] args) {
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000000000000000000000000000000000000000000000000000010L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000000000000000000000000000000000000000000000000000100L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000000000000000000000000000000000000000000000000001000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000000000000000000000000000000000000000000000000010000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000000000000000000000000000000000000000000000000100000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000000000000000000000000000000000000000000000001000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000000000000000000000000000000000000000000000010000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000000000000000000000000000000000000000000000100000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000000000000000000000000000000000000000000001000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000000000000000000000000000000000000000000010000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000000000000000000000000000000000000000000100000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000000000000000000000000000000000000000001000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000000000000000000000000000000000000000010000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000000000000000000000000000000000000000100000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000000000000000000000000000000000000001000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000000000000000000000000000000000000010000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000000000000000000000000000000000000100000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000000000000000000000000000000000001000000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000000000000000000000000000000000010000000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000000000000000000000000000000000100000000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000000000000000000000000000000001000000000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000000000000000000000000000000010000000000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000000000000000000000000000000100000000000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000000000000000000000000000001000000000000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000000000000000000000000000010000000000000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000000000000000000000000000100000000000000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000000000000000000000000001000000000000000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000000000000000000000000010000000000000000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000000000000000000000000100000000000000000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000000000000000000000001000000000000000000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000000000000000000000010000000000000000000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000000000000000000000100000000000000000000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000000000000000000001000000000000000000000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000000000000000000010000000000000000000000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000000000000000000100000000000000000000000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000000000000000001000000000000000000000000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000000000000000010000000000000000000000000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000000000000000100000000000000000000000000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000000000000001000000000000000000000000000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000000000000010000000000000000000000000000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000000000000100000000000000000000000000000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000000000001000000000000000000000000000000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000000000010000000000000000000000000000000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000000000100000000000000000000000000000000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000000001000000000000000000000000000000000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000000010000000000000000000000000000000000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000000100000000000000000000000000000000000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000001000000000000000000000000000000000000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000010000000000000000000000000000000000000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000000100000000000000000000000000000000000000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000001000000000000000000000000000000000000000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000010000000000000000000000000000000000000000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000000100000000000000000000000000000000000000000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000001000000000000000000000000000000000000000000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000010000000000000000000000000000000000000000000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000000100000000000000000000000000000000000000000000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000001000000000000000000000000000000000000000000000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000010000000000000000000000000000000000000000000000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0000100000000000000000000000000000000000000000000000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0001000000000000000000000000000000000000000000000000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0010000000000000000000000000000000000000000000000000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b0100000000000000000000000000000000000000000000000000000000000000L));
      System.out.println(
          Long.divideUnsigned(
              hideConst(12345678910111213L),
              0b1000000000000000000000000000000000000000000000000000000000000000L));
    }

    @NeverPropagateValue
    @NeverInline
    private static long hideConst(long p) {
      return p;
    }
  }
}
