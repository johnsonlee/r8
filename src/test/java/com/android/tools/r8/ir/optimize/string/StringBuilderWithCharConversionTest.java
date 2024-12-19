// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.string;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StringBuilderWithCharConversionTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines("i = 65523", "i = 13", "i = 65535", "i = 65535", "i = 32767", "i = 32768");

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
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
        .inspect(
            inspector -> {
              for (int i = 1; i < 6; i++) {
                assertTrue(
                    inspector
                        .clazz(TestClass.class)
                        .uniqueMethodWithOriginalName("f" + i)
                        .streamInstructions()
                        .noneMatch(
                            instruction ->
                                instruction.isConstNumber() || instruction.isNumberConversion()));
              }
            })
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  static class TestClass {

    @NeverInline
    public static void f1() {
      int i = -13;
      char c = (char) i;
      i = c;
      System.out.println("i = " + i);
    }

    @NeverInline
    public static void f2() {
      int i = 13;
      char c = (char) i;
      i = c;
      System.out.println("i = " + i);
    }

    @NeverInline
    public static void f3() {
      //        7------07------07------07------0
      int i = 0b00000000000000001111111111111111;
      char c = (char) i;
      i = c;
      System.out.println("i = " + i);
    }

    @NeverInline
    public static void f4() {
      //        7------07------07------07------0
      int i = 0b00000000000000011111111111111111;
      char c = (char) i;
      i = c;
      System.out.println("i = " + i);
    }

    @NeverInline
    public static void f5() {
      //        7------07------07------07------0
      int i = 0b00000000000000000111111111111111;
      char c = (char) i;
      i = c;
      System.out.println("i = " + i);
    }

    @NeverInline
    public static void f6() {
      //        7------07------07------07------0
      int i = 0b00000000000000001000000000000000;
      char c = (char) i;
      i = c;
      System.out.println("i = " + i);
    }

    public static void main(String[] args) {
      f1();
      f2();
      f3();
      f4();
      f5();
      f6();
    }
  }
}
