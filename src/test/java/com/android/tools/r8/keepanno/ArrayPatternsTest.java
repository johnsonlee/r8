// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
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
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

@RunWith(Parameterized.class)
public class ArrayPatternsTest extends KeepAnnoTestBase {

  static final String EXPECTED =
      StringUtils.lines("int[] [1, 2, 3]", "int[][] [[42]]", "Integer[][][] [[[333]]]");

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
    assertThat(inspector.clazz(B.class).method("void", "bar", "int[]"), isPresent());
    assertThat(inspector.clazz(B.class).method("void", "bar", "int[][]"), isPresent());
    assertThat(inspector.clazz(B.class).method("void", "bar", "int[][][]"), isAbsent());
    assertThat(
        inspector.clazz(B.class).method("void", "bar", "java.lang.Integer[][][]"), isPresent());
  }

  static class A {

    @UsesReflection({
      @KeepTarget(
          classConstant = B.class,
          methodName = "bar",
          methodParameterTypePatterns = {@TypePattern(constant = int[].class)}),
      @KeepTarget(
          classConstant = B.class,
          methodName = "bar",
          methodParameters = {"int[][]"}),
      @KeepTarget(
          classConstant = B.class,
          methodName = "bar",
          methodParameterTypePatterns = {@TypePattern(name = "java.lang.Integer[][][]")}),
    })
    public void foo() throws Exception {
      // Invoke the first and second method.
      B.class.getDeclaredMethod("bar", int[].class).invoke(null, (Object) new int[] {1, 2, 3});
      B.class
          .getDeclaredMethod("bar", int[][].class)
          .invoke(null, (Object) new int[][] {new int[] {42}});
      B.class
          .getDeclaredMethod("bar", Integer[][][].class)
          .invoke(null, (Object) new Integer[][][] {new Integer[][] {new Integer[] {333}}});
    }
  }

  static class B {
    public static void bar() {
      throw new RuntimeException("UNUSED");
    }

    public static void bar(int[] value) {
      System.out.println("int[] " + Arrays.toString(value));
    }

    public static void bar(int[][] value) {
      System.out.println("int[][] " + Arrays.deepToString(value));
    }

    public static void bar(int[][][] value) {
      throw new RuntimeException("UNUSED");
    }

    public static void bar(Integer[][][] value) {
      System.out.println("Integer[][][] " + Arrays.deepToString(value));
    }
  }

  static class TestClass {

    @UsedByReflection(kind = KeepItemKind.CLASS_AND_METHODS)
    public static void main(String[] args) throws Exception {
      new A().foo();
    }
  }
}
