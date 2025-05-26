// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk21.assistant;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.assistant.runtime.EmptyReflectiveOperationReceiver;
import com.android.tools.r8.assistant.runtime.ReflectiveOracle.Stack;
import com.android.tools.r8.jdk21.assistant.JavaLangTestClass21.Foo;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class JavaLangClass21Test extends TestBase {

  private static final String EXPECTED_RESULT_LOW_API =
      StringUtils.lines(
          "1",
          "ANNOTATION",
          "ANONYMOUS_CLASS",
          "ARRAY",
          "ENUM",
          "HIDDEN",
          "Missing isHidden",
          "INTERFACE",
          "LOCAL_CLASS",
          "MEMBER_CLASS",
          "PRIMITIVE",
          "RECORD",
          "Missing isRecord",
          "SEALED",
          "Missing isSealed",
          "SYNTHETIC");

  private static final String EXPECTED_RESULT_U =
      StringUtils.lines(
          "1",
          "ANNOTATION",
          "ANONYMOUS_CLASS",
          "ARRAY",
          "ENUM",
          "HIDDEN",
          "Missing isHidden",
          "INTERFACE",
          "LOCAL_CLASS",
          "MEMBER_CLASS",
          "PRIMITIVE",
          "RECORD",
          "SEALED",
          "SYNTHETIC");

  private static final String EXPECTED_RESULT_HIGH_API =
      StringUtils.lines(
          "1",
          "ANNOTATION",
          "ANONYMOUS_CLASS",
          "ARRAY",
          "ENUM",
          "HIDDEN",
          "INTERFACE",
          "LOCAL_CLASS",
          "MEMBER_CLASS",
          "PRIMITIVE",
          "RECORD",
          "SEALED",
          "SYNTHETIC");

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNativeMultidexDexRuntimes().withMaximumApiLevel().build();
  }

  @Test
  public void testInstrumentationWithCustomOracle() throws Exception {
    testForAssistant()
        .addProgramClasses(JavaLangTestClass21.class, Foo.class)
        .addStrippedOuter(getClass())
        .addInstrumentationClasses(Instrumentation.class)
        .setCustomReflectiveOperationReceiver(Instrumentation.class)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), JavaLangTestClass21.class)
        .assertSuccessWithOutput(
            parameters.getApiLevel().isLessThan(AndroidApiLevel.U)
                ? EXPECTED_RESULT_LOW_API
                : (parameters.getApiLevel().isEqualTo(AndroidApiLevel.U)
                    ? EXPECTED_RESULT_U
                    : EXPECTED_RESULT_HIGH_API));
  }

  public static class Instrumentation extends EmptyReflectiveOperationReceiver {

    private void printNumIfTrue(boolean correct, int num) {
      if (correct) {
        System.out.println(num);
      } else {
        System.out.println("fail");
      }
    }

    @Override
    public void onClassForName(
        Stack stack, String className, boolean initialize, ClassLoader classLoader) {
      printNumIfTrue(className.endsWith("Foo"), 1);
    }

    @Override
    public void onClassFlag(Stack stack, Class<?> clazz, ClassFlag classFlag) {
      System.out.println(classFlag);
    }
  }
}
