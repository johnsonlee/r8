// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.assistant.runtime.ReflectiveOperationReceiver;
import com.android.tools.r8.assistant.runtime.ReflectiveOracle.Stack;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class R8AssistentReflectiveInstrumentationTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNativeMultidexDexRuntimes().withMaximumApiLevel().build();
  }

  @Test
  public void testR8LibRuntimeFiles() throws IOException {
    assumeTrue(ToolHelper.isTestingR8Lib());
    ZipUtils.iter(
        ToolHelper.ASSISTANT_JAR,
        zipEntry -> {
          try {
            if (ZipUtils.isClassFile(zipEntry.getName())) {
              byte[] bytesAssistant =
                  ZipUtils.readSingleEntry(ToolHelper.ASSISTANT_JAR, zipEntry.getName());
              byte[] bytesR8Lib =
                  ZipUtils.readSingleEntry(ToolHelper.R8LIB_JAR, zipEntry.getName());
              assertArrayEquals(bytesAssistant, bytesR8Lib);
            }
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        });
  }

  @Test
  public void testInstrumentation() throws Exception {
    testForAssistant()
        .addProgramClasses(TestClass.class, Foo.class, Bar.class)
        .setMinApi(parameters)
        .compile()
        .inspectOriginalDex(inspector -> inspectStaticCallsInReflectOn(1, inspector))
        .inspect(inspector -> inspectStaticCallsInReflectOn(4, inspector))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(
            "Reflectively created new instance of " + Bar.class.getName(),
            "Reflectively got declared method callMe on " + Bar.class.getName(),
            "Reflectively called Class.forName on " + Bar.class.getName());
  }

  @Test
  public void testInstrumentationWithCustomOracle() throws Exception {
    testForAssistant()
        .addProgramClasses(TestClass.class, Foo.class, Bar.class)
        .addInstrumentationClasses(InstrumentationClass.class)
        .setCustomReflectiveOperationReceiver(InstrumentationClass.class)
        .setMinApi(parameters)
        .compile()
        .inspectOriginalDex(inspector -> inspectStaticCallsInReflectOn(1, inspector))
        .inspect(inspector -> inspectStaticCallsInReflectOn(4, inspector))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(
            "Custom receiver " + Bar.class.getName(),
            "Custom receiver method callMe",
            "Custom receiver classForName " + Bar.class.getName());
  }

  @Test
  public void testStack() throws Exception {
    testForAssistant()
        .addProgramClasses(TestClass.class, Foo.class, Bar.class)
        .addInstrumentationClasses(TestReflectiveOperationReceiverStackHandler.class)
        .setCustomReflectiveOperationReceiver(
            descriptor(TestReflectiveOperationReceiverStackHandler.class))
        .setMinApi(parameters)
        .compile()
        .inspectOriginalDex(inspector -> inspectStaticCallsInReflectOn(1, inspector))
        .inspect(inspector -> inspectStaticCallsInReflectOn(4, inspector))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("correct", "correct", "correct");
  }

  // Injected into the app by the R8Assistant.
  public static class TestReflectiveOperationReceiverStackHandler
      implements ReflectiveOperationReceiver {

    int lineNumberOfNewInstance = -1;

    @Override
    public void onClassForName(Stack stack, String className) {
      if (!className.equals(Bar.class.getName())) {
        throw new RuntimeException("Wrong class name passed");
      }
      int lineNumberOfTopOfStack = getLineNumberOfTopOfStack(stack);
      if (lineNumberOfTopOfStack != lineNumberOfNewInstance + 2) {
        throw new RuntimeException("Wrong line number on top of stack " + lineNumberOfTopOfStack);
      }
      ensureCorrectStack(stack);
    }

    private void ensureCorrectStack(Stack stack) {
      StackTraceElement[] stackTraceElements = stack.getStackTraceElements();
      if (stackTraceElements.length != 2) {
        // Only main and reflectOn should be in the stack
        throw new RuntimeException("Wrong stack hight of " + stackTraceElements.length);
      }
      String topOfStack = stack.getStackTraceElements()[0].toString();
      String secondToTopOfStack = stack.getStackTraceElements()[1].toString();
      String sourceFile = "R8AssistentReflectiveInstrumentationTest";
      if (!topOfStack.contains("reflectOn(" + sourceFile)) {
        throw new RuntimeException("reflectOn must be top of stack, got " + topOfStack);
      }
      if (!secondToTopOfStack.contains("main(" + sourceFile)) {
        throw new RuntimeException("main must be second to top of stack");
      }
      System.out.println("correct");
    }

    private int getLineNumberOfTopOfStack(Stack stack) {
      return stack.getStackTraceElements()[0].getLineNumber();
    }

    @Override
    public void onClassNewInstance(Stack stack, Class<?> clazz) {
      if (!clazz.equals(Bar.class)) {
        throw new RuntimeException("Wrong class passed");
      }
      ensureCorrectStack(stack);
      lineNumberOfNewInstance = getLineNumberOfTopOfStack(stack);
    }

    @Override
    public void onClassGetDeclaredMethod(
        Stack stack, Class<?> clazz, String method, Class<?>... parameters) {
      if (!clazz.equals(Bar.class) || !method.equals("callMe")) {
        throw new RuntimeException("Wrong method passed");
      }
      int lineNumberOfTopOfStack = getLineNumberOfTopOfStack(stack);
      if (lineNumberOfTopOfStack != lineNumberOfNewInstance + 1) {
        throw new RuntimeException("Wrong line number on top of stack " + lineNumberOfTopOfStack);
      }
      ensureCorrectStack(stack);
    }

    @Override
    public boolean requiresStackInformation() {
      return true;
    }
  }

  private static void inspectStaticCallsInReflectOn(int count, CodeInspector inspector) {
    ClassSubject testClass = inspector.clazz(TestClass.class);
    assertThat(testClass, isPresent());
    MethodSubject reflectOn = testClass.uniqueMethodWithOriginalName("reflectOn");
    assertThat(reflectOn, isPresent());
    long codeCount =
        reflectOn.streamInstructions().filter(InstructionSubject::isInvokeStatic).count();
    assertEquals(count, codeCount);
  }

  public static class InstrumentationClass implements ReflectiveOperationReceiver {

    @Override
    public void onClassForName(Stack stack, String className) {
      System.out.println("Custom receiver classForName " + className);
    }

    @Override
    public void onClassNewInstance(Stack stack, Class<?> clazz) {
      System.out.println("Custom receiver " + clazz.getName());
    }

    @Override
    public void onClassGetDeclaredMethod(
        Stack stack, Class<?> clazz, String method, Class<?>... parameters) {
      System.out.println("Custom receiver method " + method);
    }
  }

  static class TestClass {
    public static void main(String[] args) {
      reflectOn(System.currentTimeMillis() == 0 ? Foo.class : Bar.class);
    }

    public static void reflectOn(Class<?> clazz) {
      try {
        clazz.newInstance();
        clazz.getDeclaredMethod("callMe");
        Class.forName(clazz.getName());
      } catch (InstantiationException
          | IllegalAccessException
          | NoSuchMethodException
          | ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public static class Foo {}

  public static class Bar {
    public void callMe() {}
  }
}
