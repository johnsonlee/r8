// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.assistant;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.assistant.runtime.EmptyReflectiveOperationReceiver;
import com.android.tools.r8.assistant.runtime.ReflectiveOracle.Stack;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ServiceLoaderTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNativeMultidexDexRuntimes().withMaximumApiLevel().build();
  }

  @Test
  public void testInstrumentationWithCustomOracle() throws Exception {
    testForAssistant()
        .addProgramClassesAndInnerClasses(ServiceLoaderTestClass.class)
        .addInstrumentationClasses(Instrumentation.class)
        .setCustomReflectiveOperationReceiver(Instrumentation.class)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), ServiceLoaderTestClass.class)
        .assertSuccessWithOutputLines(
            "com.android.tools.r8.assistant.ServiceLoaderTestClass$NameService",
            "com.android.tools.r8.assistant.ServiceLoaderTestClass$NameService",
            "com.android.tools.r8.assistant.ServiceLoaderTestClass$NameService");
  }

  public static class Instrumentation extends EmptyReflectiveOperationReceiver {

    @Override
    public void onServiceLoaderLoad(Stack stack, Class<?> clazz, ClassLoader classLoader) {
      System.out.println(clazz.getName());
    }
  }
}
