// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.keepanno.annotations.ClassNamePattern;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.KeepTarget;
import com.android.tools.r8.keepanno.annotations.StringPattern;
import com.android.tools.r8.keepanno.annotations.TypePattern;
import com.android.tools.r8.keepanno.annotations.UsedByReflection;
import com.android.tools.r8.keepanno.annotations.UsesReflection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

@RunWith(Parameterized.class)
public class ClassNameStringPatternsTest extends KeepAnnoTestBase {

  static final String EXPECTED = StringUtils.lines("106");

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
    return ImmutableList.of(TestClass.class, A.class, B.class, Foo.class, Bar.class, FooBar.class);
  }

  private void checkOutput(CodeInspector inspector) {
    ClassSubject bClass = inspector.clazz(B.class);
    assertThat(bClass, isPresentAndRenamed());
    // prefix Foo
    assertThat(bClass.method("int", "m", typeName(Foo.class)), isPresent());
    assertThat(bClass.method("int", "m", typeName(Bar.class)), isAbsent());
    assertThat(bClass.method("int", "m", typeName(FooBar.class)), isPresent());
    // suffix Bar
    assertThat(bClass.method("int", "g", typeName(Foo.class)), isAbsent());
    assertThat(bClass.method("int", "g", typeName(Bar.class)), isPresent());
    assertThat(bClass.method("int", "g", typeName(FooBar.class)), isPresent());
  }

  static class A {

    @UsesReflection({
      @KeepTarget(classConstant = Foo.class),
      @KeepTarget(classConstant = Bar.class),
      @KeepTarget(classConstant = FooBar.class),
      @KeepTarget(
          classConstant = B.class,
          methodName = "m",
          methodParameterTypePatterns = {
            @TypePattern(
                classNamePattern =
                    @ClassNamePattern(
                        unqualifiedNamePattern =
                            @StringPattern(startsWith = "ClassNameStringPatternsTest$Foo")))
          }),
      @KeepTarget(
          classConstant = B.class,
          methodName = "g",
          methodParameterTypePatterns = {
            @TypePattern(
                classNamePattern =
                    @ClassNamePattern(unqualifiedNamePattern = @StringPattern(endsWith = "Bar")))
          }),
    })
    public void foo() throws Exception {
      int counter = 0;
      for (Method method : B.class.getDeclaredMethods()) {
        String name = method.getName();
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length != 1) {
          continue;
        }
        Class<?> parameterType = parameterTypes[0];
        if (name.startsWith("m")
            && unqualifiedClassName(parameterType).startsWith("ClassNameStringPatternsTest$Foo")) {
          counter += (int) method.invoke(null, new Object[] {null});
        }
        if (name.startsWith("g") && unqualifiedClassName(parameterType).endsWith("Bar")) {
          counter += (int) method.invoke(null, new Object[] {null});
        }
      }
      System.out.println(counter);
    }

    private String unqualifiedClassName(Class<?> clazz) {
      String name = clazz.getName();
      int packageEnd = name.lastIndexOf('.');
      int start = packageEnd > 0 ? packageEnd + 1 : 0;
      return name.substring(start);
    }
  }

  static class Foo {}

  static class Bar {}

  static class FooBar {}

  static class B {
    public static int m(Foo f) {
      return 2;
    }

    public static int m(Bar f) {
      return 4;
    }

    public static int m(FooBar f) {
      return 8;
    }

    public static int g(Foo f) {
      return 16;
    }

    public static int g(Bar f) {
      return 32;
    }

    public static int g(FooBar f) {
      return 64;
    }
  }

  static class TestClass {

    @UsedByReflection(kind = KeepItemKind.CLASS_AND_METHODS)
    public static void main(String[] args) throws Exception {
      new A().foo();
    }
  }
}
