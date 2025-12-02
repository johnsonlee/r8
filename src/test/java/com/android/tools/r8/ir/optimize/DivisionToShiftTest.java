// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.errors.Unreachable;
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
public class DivisionToShiftTest extends TestBase {

  private static final String EXPECTED_OUTPUT_INT =
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

  private static final String EXPECTED_OUTPUT_LONG =
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

  @Parameterized.Parameter(0)
  public TestParameters parameters;

  @Parameterized.Parameter(1)
  public CompilationMode mode;

  @Parameterized.Parameter(2)
  public Type type;

  @Parameters(name = "{0}, {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        CompilationMode.values(),
        new Type[] {Type.INT, Type.LONG});
  }

  public enum Type {
    INT,
    LONG
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    Class<?> testClass = getPositiveTestClass();
    testForJvm(parameters)
        .addProgramClasses(testClass)
        .run(parameters.getRuntime(), testClass)
        .assertSuccessWithOutput(getExpectedOutput());
  }

  @Test
  public void testR8Positive() throws Exception {
    Class<?> testClass = getPositiveTestClass();
    testForR8(parameters)
        .addProgramClasses(testClass)
        .setMode(mode)
        .applyIf(type == Type.LONG, R8TestBuilder::enableMemberValuePropagationAnnotations)
        .applyIf(type == Type.LONG, R8TestBuilder::enableInliningAnnotations)
        .addKeepMainRule(testClass)
        .collectSyntheticItems()
        .compile()
        .inspectWithSyntheticItems(
            (inspector, syntheticItemsTestUtils) -> {
              MethodSubject mainMethod = inspector.clazz(testClass).mainMethod();
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
                  assertEquals(expectedDivisionCount, divideUnsignedCallCount(mainMethod, type));
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
                  long backportCallCount =
                      backportCallCount(syntheticItemsTestUtils, mainMethod, testClass, type);
                  assertEquals(expectedDivisionCount, backportCallCount);
                }
              }
            })
        .run(parameters.getRuntime(), testClass)
        .assertSuccessWithOutput(getExpectedOutput());
  }

  @Test
  public void testD8Positive() throws Exception {
    parameters.assumeDexRuntime();
    Class<?> testClass = getPositiveTestClass();
    testForD8(parameters)
        .addProgramClasses(testClass)
        .setMode(mode)
        .collectSyntheticItems()
        .compile()
        .inspectWithSyntheticItems(
            (inspector, syntheticItemsTestUtils) -> {
              MethodSubject mainMethod = inspector.clazz(testClass).mainMethod();
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
                  assertEquals(expectedDivisionCount, divideUnsignedCallCount(mainMethod, type));
                }
              } else {
                assertEquals(
                    expectedDivisionCount,
                    backportCallCount(syntheticItemsTestUtils, mainMethod, testClass, type));
              }
            })
        .run(parameters.getRuntime(), testClass)
        .assertSuccessWithOutput(getExpectedOutput());
  }

  private Class<?> getPositiveTestClass() {
    switch (type) {
      case INT:
        return IntPositiveTest.class;
      case LONG:
        return LongPositiveTest.class;
      default:
        throw new Unreachable();
    }
  }

  private int getExpectedDivisionCount(
      boolean isDivideUnsignedSupported, boolean isBackportInlined) {
    switch (type) {
      case INT:
        return 31;
      case LONG:
        if (!isDivideUnsignedSupported && isBackportInlined) {
          // Each backport has two divisions.
          // The last inlined backport is folded into a constant.
          return 63 * 2 - 2;
        }
        return 63;
      default:
        throw new Unreachable();
    }
  }

  private String getExpectedOutput() {
    switch (type) {
      case INT:
        return EXPECTED_OUTPUT_INT;
      case LONG:
        return EXPECTED_OUTPUT_LONG;
      default:
        throw new Unreachable();
    }
  }

  /**
   * @param type must be INT or LONG.
   */
  private static long backportCallCount(
      SyntheticItemsTestUtils syntheticItemsTestUtils,
      MethodSubject mainMethod,
      Class<?> testClass,
      Type type)
      throws NoSuchMethodException {
    Method baseDivisionMethod;
    switch (type) {
      case INT:
        baseDivisionMethod = Integer.class.getMethod("divideUnsigned", int.class, int.class);
        break;
      case LONG:
        baseDivisionMethod = Long.class.getMethod("divideUnsigned", long.class, long.class);
        break;
      default:
        throw new Unreachable();
    }
    MethodReference backportMethod =
        syntheticItemsTestUtils.syntheticBackportMethod(testClass, 0, baseDivisionMethod);
    return mainMethod
        .streamInstructions()
        .filter(CodeMatchers.isInvokeWithTarget(backportMethod))
        .count();
  }

  /**
   * @param type must be INT or LONG.
   */
  private static long divideUnsignedCallCount(MethodSubject method, Type type) {
    String holder;
    switch (type) {
      case INT:
        holder = "java.lang.Integer";
        break;
      case LONG:
        holder = "java.lang.Long";
        break;
      default:
        throw new Unreachable();
    }
    return method
        .streamInstructions()
        .filter(CodeMatchers.isInvokeWithTarget(holder, "divideUnsigned"))
        .count();
  }

  public static class IntPositiveTest {
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

  public static class LongPositiveTest {
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
