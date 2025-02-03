// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.KeepTarget;
import com.android.tools.r8.keepanno.annotations.TypePattern;
import com.android.tools.r8.keepanno.annotations.UsedByReflection;
import com.android.tools.r8.keepanno.annotations.UsesReflection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

@RunWith(Parameterized.class)
public class MethodPatternsTest extends KeepAnnoTestBase {

  static final String EXPECTED =
      StringUtils.lines("Hello 42", "Hello 12", "int", "long", "class java.lang.Integer");

  @Parameter public KeepAnnoParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static List<KeepAnnoParameters> data() {
    return createParameters(
        getTestParameters().withDefaultRuntimes().withMaximumApiLevel().build());
  }

  @Test
  public void test() throws Exception {
    testForKeepAnno(parameters)
        .addProgramClasses(getInputClasses())
        .setExcludedOuterClass(getClass())
        .run(TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .applyIf(parameters.isShrinker(), r -> r.inspect(this::checkOutput));
  }

  public List<Class<?>> getInputClasses() {
    return ImmutableList.of(TestClass.class, A.class, B.class);
  }

  private void checkOutput(CodeInspector inspector) {
    assertThat(inspector.clazz(B.class), isPresentAndRenamed());
    assertThat(inspector.clazz(B.class).method("void", "bar"), isAbsent());
    assertThat(inspector.clazz(B.class).method("void", "bar", "int"), isPresent());
    assertThat(inspector.clazz(B.class).method("void", "bar", "int", "int"), isPresent());
    assertThat(
        inspector.clazz(B.class).method("void", "bar", "java.lang.Object", "java.lang.Object"),
        isAbsent());
    assertThat(
        inspector.clazz(B.class).method("int", "bar", "int", "long", "java.lang.Integer"),
        isPresent());
    assertThat(
        inspector.clazz(B.class).method("int", "bar", "int", "long", "java.lang.Integer", "int"),
        isAbsent());
  }

  static class A {

    @UsesReflection({
      @KeepTarget(
          classConstant = B.class,
          methodName = "bar",
          methodReturnTypePattern = @TypePattern(),
          methodParameterTypePatterns = {@TypePattern(constant = int.class)}),
      @KeepTarget(
          classConstant = B.class,
          methodName = "bar",
          methodReturnTypeConstant = void.class,
          methodParameterTypePatterns = {
            @TypePattern(constant = int.class),
            @TypePattern(name = "int")
          }),
      @KeepTarget(
          classConstant = B.class,
          methodName = "bar",
          methodReturnTypeConstant = int.class,
          methodParameterTypePatterns = {@TypePattern, @TypePattern, @TypePattern}),
    })
    public void foo() throws Exception {
      // Invoke the first and second method.
      B.class.getDeclaredMethod("bar", int.class).invoke(null, 42);
      B.class.getDeclaredMethod("bar", int.class, int.class).invoke(null, 1, 2);
      // Print args of third method.
      for (Method method : B.class.getDeclaredMethods()) {
        if (method.getReturnType().equals(int.class) && method.getParameterCount() == 3) {
          for (Class<?> type : method.getParameterTypes()) {
            System.out.println(type);
          }
        }
      }
    }
  }

  static class B {
    public static void bar() {
      throw new RuntimeException("UNUSED");
    }

    public static void bar(int value) {
      System.out.println("Hello " + value);
    }

    public static void bar(int value1, int value2) {
      System.out.println("Hello " + value1 + value2);
    }

    public static void bar(Object value1, Object value2) {
      throw new RuntimeException("UNUSED");
    }

    public static int bar(int value1, long value2, Integer value3) {
      System.out.println("Hello " + value1 + value2 + value3);
      return value1 + (int) value2 + value3;
    }

    public static int bar(int value1, long value2, Integer value3, int value4) {
      throw new RuntimeException("UNUSED");
    }
  }

  static class TestClass {

    @UsedByReflection(kind = KeepItemKind.CLASS_AND_METHODS)
    public static void main(String[] args) throws Exception {
      new A().foo();
    }
  }
}
