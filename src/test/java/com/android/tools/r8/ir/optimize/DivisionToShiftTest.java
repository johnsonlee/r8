// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeMatchers;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DivisionToShiftTest extends TestBase {

  private static final String EXPECTED_OUTPUT = StringUtils.lines("10");

  @Parameterized.Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static final Matcher<MethodSubject> invokesIntegerDivideUnsigned =
      CodeMatchers.invokesMethod(
          "int", "java.lang.Integer", "divideUnsigned", ImmutableList.of("int", "int"));

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
                assertThat(mainMethod, invokesIntegerDivideUnsigned);
              } else {
                assertThat(
                    "uses inlined backport division",
                    Iterators.any(
                        mainMethod.iterateInstructions(), InstructionSubject::isDivision));
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
                assertThat(mainMethod, invokesIntegerDivideUnsigned);
              } else {
                MethodReference backportMethod =
                    syntheticItemsTestUtils.syntheticBackportMethod(
                        Main.class,
                        0,
                        Integer.class.getMethod("divideUnsigned", int.class, int.class));
                assertThat(mainMethod, CodeMatchers.invokesMethod(backportMethod));
              }
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
    ;
  }

  public static class Main {
    public static void main(String[] args) {
      System.out.println(Integer.divideUnsigned(84, 8));
    }
  }
}
