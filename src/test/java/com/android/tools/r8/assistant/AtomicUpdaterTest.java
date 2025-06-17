// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.assistant;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.assistant.JavaLangClassTestClass.Bar;
import com.android.tools.r8.assistant.JavaLangClassTestClass.Foo;
import com.android.tools.r8.assistant.runtime.EmptyReflectiveOperationReceiver;
import com.android.tools.r8.assistant.runtime.ReflectiveOracle.Stack;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AtomicUpdaterTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNativeMultidexDexRuntimes().withMaximumApiLevel().build();
  }

  @Test
  public void testInstrumentationWithCustomOracle() throws Exception {
    testForAssistant()
        .addProgramClasses(AtomicUpdaterTestClass.class, Foo.class, Bar.class)
        .addInstrumentationClasses(Instrumentation.class)
        .setCustomReflectiveOperationReceiver(Instrumentation.class)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), AtomicUpdaterTestClass.class)
        .assertSuccessWithOutputLines(
            "int com.android.tools.r8.assistant.AtomicUpdaterTestClass#i",
            "42",
            "long com.android.tools.r8.assistant.AtomicUpdaterTestClass#l",
            "42",
            "java.lang.Object com.android.tools.r8.assistant.AtomicUpdaterTestClass#o",
            "42");
  }

  public static class Instrumentation extends EmptyReflectiveOperationReceiver {

    @Override
    public void onAtomicIntegerFieldUpdaterNewUpdater(Stack stack, Class<?> clazz, String name) {
      System.out.println("int " + clazz.getName() + "#" + name);
    }

    @Override
    public void onAtomicLongFieldUpdaterNewUpdater(Stack stack, Class<?> clazz, String name) {
      System.out.println("long " + clazz.getName() + "#" + name);
    }

    @Override
    public void onAtomicReferenceFieldUpdaterNewUpdater(
        Stack stack, Class<?> clazz, Class<?> fieldClass, String name) {
      System.out.println(fieldClass.getName() + " " + clazz.getName() + "#" + name);
    }
  }
}
