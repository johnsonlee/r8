// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno;

import static org.junit.Assert.assertThrows;

import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.keepanno.annotations.ClassNamePattern;
import com.android.tools.r8.keepanno.annotations.InstanceOfPattern;
import com.android.tools.r8.keepanno.annotations.KeepTarget;
import com.android.tools.r8.keepanno.annotations.TypePattern;
import com.android.tools.r8.keepanno.annotations.UsesReflection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

@RunWith(Parameterized.class)
public class KeepTypePatternWithInstanceOfTest extends KeepAnnoTestBase {

  @Parameter public KeepAnnoParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static List<KeepAnnoParameters> data() {
    return createParameters(
        getTestParameters()
            .withDefaultRuntimes()
            .withApiLevel(AndroidApiLevel.B)
            .enableApiLevelsForCf()
            .build());
  }

  @Test
  public void test() throws Exception {
    if (parameters.isReference() || parameters.isNativeR8()) {
      testForKeepAnno(parameters)
          .addInnerClasses(getClass())
          .addKeepMainRule(TestClass.class)
          .run(TestClass.class)
          .assertSuccessWithOutput(getExpectedResult(parameters.isReference() ? 9 : 4));
    } else {
      // It's either Unimplemented or CompilationFailedException.
      assertThrows(
          Exception.class,
          () ->
              testForKeepAnno(parameters)
                  .addInnerClasses(getClass())
                  .addKeepMainRule(TestClass.class)
                  .run(TestClass.class));
    }
  }

  private String getExpectedResult(int numMethods) {
    return StringUtils.lines(
        "Num methods: " + numMethods,
        "5",
        "8",
        "9",
        "6",
        "1",
        "2",
        "3",
        "4",
        "5",
        "6",
        "7",
        "8",
        "9");
  }

  private void clearClassMerging(TestShrinkerBuilder<?, ?, ?, ?, ?> sb) {
    sb.addOptionsModification(
        opt -> {
          opt.horizontalClassMergerOptions().disable();
          opt.getVerticalClassMergerOptions().disable();
        });
  }

  public static class Top {}

  public static class Top1 extends Top {}

  public static class Sub1 extends Top1 {}

  public static class Subb1 extends Top1 {}

  public static class Top2 extends Top {}

  public static class Sub2 extends Top2 {}

  public static class Subb2 extends Top2 {}

  public static class A {

    public void foo(Top1 a, Top2 b) {
      System.out.println("1");
    }

    public void foo(Sub1 a, Top2 b) {
      System.out.println("2");
    }

    public void foo(Subb1 a, Top2 b) {
      System.out.println("3");
    }

    public void foo(Top1 a, Sub2 b) {
      System.out.println("4");
    }

    public void foo(Sub1 a, Sub2 b) {
      System.out.println("5");
    }

    public void foo(Subb1 a, Sub2 b) {
      System.out.println("6");
    }

    public void foo(Top1 a, Subb2 b) {
      System.out.println("7");
    }

    public void foo(Sub1 a, Subb2 b) {
      System.out.println("8");
    }

    public void foo(Subb1 a, Subb2 b) {
      System.out.println("9");
    }
  }

  static class TestClass {

    public static void main(String[] args)
        throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
      System.out.println("Num methods: " + A.class.getDeclaredMethods().length);
      for (Method method : reflectiveMethods()) {
        method.invoke(new A(), null, null);
      }
      // Make sure all the methods are live, they can be inlined.
      runAll();
    }

    private static void runAll() {
      A a = new A();
      a.foo(new Top1(), new Top2());
      a.foo(new Sub1(), new Top2());
      a.foo(new Subb1(), new Top2());
      a.foo(new Top1(), new Sub2());
      a.foo(new Sub1(), new Sub2());
      a.foo(new Subb1(), new Sub2());
      a.foo(new Top1(), new Subb2());
      a.foo(new Sub1(), new Subb2());
      a.foo(new Subb1(), new Subb2());
    }

    @UsesReflection(
        @KeepTarget(
            classConstant = A.class,
            methodName = "foo",
            methodParameterTypePatterns = {
              @TypePattern(
                  instanceOfPattern =
                      @InstanceOfPattern(
                          inclusive = false,
                          classNamePattern = @ClassNamePattern(constant = Top1.class))),
              @TypePattern(
                  instanceOfPattern =
                      @InstanceOfPattern(
                          inclusive = false,
                          classNamePattern = @ClassNamePattern(constant = Top2.class)))
            }))
    public static Method[] reflectiveMethods() throws NoSuchMethodException {
      return new Method[] {
        A.class.getDeclaredMethod(getFoo(), Sub1.class, Sub2.class),
        A.class.getDeclaredMethod(getFoo(), Sub1.class, Subb2.class),
        A.class.getDeclaredMethod(getFoo(), Subb1.class, Subb2.class),
        A.class.getDeclaredMethod(getFoo(), Subb1.class, Sub2.class)
      };
    }

    private static String getFoo() {
      return System.currentTimeMillis() > 0 ? "foo" : "bar";
    }
  }
}
