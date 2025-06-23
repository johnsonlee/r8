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
public class JavaLangClassTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNativeMultidexDexRuntimes().withMaximumApiLevel().build();
  }

  @Test
  public void testInstrumentationWithCustomOracle() throws Exception {
    testForAssistant()
        .addProgramClasses(JavaLangClassTestClass.class, Foo.class, Bar.class)
        .addInstrumentationClasses(Instrumentation.class)
        .setCustomReflectiveOperationReceiver(Instrumentation.class)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), JavaLangClassTestClass.class)
        .assertSuccessWithOutputLines(
            "5", "1", "9", "2", "3", "3", "4", "32", "30", "31", "5", "6", "7", "8", "5", "1", "12",
            "13", "15", "22", "33", "35", "20", "21", "34", "42", "41", "40");
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
    public void onClassGetComponentType(Stack stack, Class<?> clazz) {
      printNumIfTrue(clazz.toString().endsWith("Foo"), 12);
    }

    @Override
    public void onClassGetPackage(Stack stack, Class<?> clazz) {
      printNumIfTrue(clazz.toString().endsWith("Foo"), 13);
    }

    @Override
    public void onClassIsAssignableFrom(Stack stack, Class<?> clazz, Class<?> sup) {
      printNumIfTrue(clazz.toString().endsWith("Foo"), 15);
    }

    @Override
    public void onClassGetDeclaredMethod(
        Stack stack, Class<?> clazz, String method, Class<?>... parameters) {
      printNumIfTrue(clazz.getName().endsWith("Foo"), 2);
    }

    @Override
    public void onClassGetDeclaredMethods(Stack stack, Class<?> clazz) {
      printNumIfTrue(clazz.getName().endsWith("Foo"), 4);
    }

    @Override
    public void onClassGetDeclaredField(Stack stack, Class<?> clazz, String fieldName) {
      printNumIfTrue(
          clazz.getName().endsWith("Foo") && (fieldName.equals("a") || fieldName.equals("b")), 3);
    }

    @Override
    public void onClassGetDeclaredFields(Stack stack, Class<?> clazz) {
      printNumIfTrue(clazz.getName().endsWith("Foo"), 32);
    }

    @Override
    public void onClassGetDeclaredConstructor(Stack stack, Class<?> clazz, Class<?>... parameters) {
      printNumIfTrue(clazz.getName().endsWith("Foo"), 30);
    }

    @Override
    public void onClassGetDeclaredConstructors(Stack stack, Class<?> clazz) {
      printNumIfTrue(clazz.getName().endsWith("Foo"), 31);
    }

    @Override
    public void onClassGetMethod(
        Stack stack, Class<?> clazz, String method, Class<?>... parameters) {
      printNumIfTrue(clazz.getName().endsWith("Bar"), 20);
    }

    @Override
    public void onClassGetMethods(Stack stack, Class<?> clazz) {
      printNumIfTrue(clazz.getName().endsWith("Bar"), 22);
    }

    @Override
    public void onClassGetField(Stack stack, Class<?> clazz, String fieldName) {
      printNumIfTrue(clazz.getName().endsWith("Bar"), 21);
    }

    @Override
    public void onClassGetFields(Stack stack, Class<?> clazz) {
      printNumIfTrue(clazz.getName().endsWith("Bar"), 33);
    }

    @Override
    public void onClassGetConstructor(Stack stack, Class<?> clazz, Class<?>... parameters) {
      printNumIfTrue(clazz.getName().endsWith("Bar"), 34);
    }

    @Override
    public void onClassGetConstructors(Stack stack, Class<?> clazz) {
      printNumIfTrue(clazz.getName().endsWith("Bar"), 35);
    }

    @Override
    public void onClassGetName(Stack stack, Class<?> clazz, NameLookupType lookupType) {
      if (lookupType == NameLookupType.NAME) {
        printNumIfTrue(clazz.getName().endsWith("Foo"), 5);
      }
      if (lookupType == NameLookupType.CANONICAL_NAME) {
        printNumIfTrue(clazz.getName().endsWith("Foo"), 6);
      }
      if (lookupType == NameLookupType.SIMPLE_NAME) {
        printNumIfTrue(clazz.getName().endsWith("Foo"), 7);
      }
      if (lookupType == NameLookupType.TYPE_NAME) {
        printNumIfTrue(clazz.getName().endsWith("Foo"), 8);
      }
    }

    @Override
    public void onClassNewInstance(Stack stack, Class<?> clazz) {
      super.onClassNewInstance(stack, clazz);
    }

    @Override
    public void onClassGetSuperclass(Stack stack, Class<?> clazz) {
      printNumIfTrue(clazz.getName().endsWith("Foo"), 9);
    }

    @Override
    public void onClassAsSubclass(Stack stack, Class<?> holder, Class<?> clazz) {
      printNumIfTrue(holder.getName().endsWith("Bar"), 40);
    }

    @Override
    public void onClassIsInstance(Stack stack, Class<?> holder, Object object) {
      printNumIfTrue(holder.getName().endsWith("Bar"), 41);
    }

    @Override
    public void onClassCast(Stack stack, Class<?> holder, Object object) {
      printNumIfTrue(holder.getName().endsWith("Bar"), 42);
    }
  }
}
