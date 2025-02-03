// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.keepanno.annotations.AnnotationPattern;
import com.android.tools.r8.keepanno.annotations.ClassNamePattern;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.KeepTarget;
import com.android.tools.r8.keepanno.annotations.UsedByReflection;
import com.android.tools.r8.keepanno.annotations.UsesReflection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

@RunWith(Parameterized.class)
public class AnnotationPatternMultipleTest extends KeepAnnoTestBase {

  static final String EXPECTED = StringUtils.lines("C1: A1", "C2:");

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
    return ImmutableList.of(
        TestClass.class, Reflector.class, A1.class, A2.class, A3.class, C1.class, C2.class);
  }

  private void checkOutput(CodeInspector inspector) {
    assertThat(inspector.clazz(A1.class), isPresentAndRenamed());
    assertThat(inspector.clazz(A2.class), isPresentAndRenamed());
    // No use of A3 so R8 will remove it.
    assertThat(inspector.clazz(A3.class), isAbsent());
    // The class is retained by the keep-annotation.
    ClassSubject c1 = inspector.clazz(C1.class);
    assertThat(c1, isPresentAndNotRenamed());
    assertThat(c1.annotation(A1.class), isPresent());
    assertThat(c1.annotation(A3.class), isAbsent());

    ClassSubject c2 = inspector.clazz(C2.class);
    assertThat(c2, isPresentAndNotRenamed());
    assertThat(c2.annotation(A2.class), isPresent());
    assertThat(c2.annotation(A3.class), isAbsent());
  }

  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @interface A1 {}

  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.CLASS)
  @interface A2 {}

  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @interface A3 {}

  static class Reflector {

    @UsesReflection(
        @KeepTarget(
            classAnnotatedByClassNamePattern =
                @ClassNamePattern(packageName = "com.android.tools.r8.keepanno"),
            constrainAnnotations = {
              @AnnotationPattern(constant = A1.class, retention = RetentionPolicy.RUNTIME),
              @AnnotationPattern(constant = A2.class, retention = RetentionPolicy.CLASS)
            }))
    public void foo(Class<?>... classes) throws Exception {
      for (Class<?> clazz : classes) {
        String typeName = clazz.getTypeName();
        System.out.print(typeName.substring(typeName.lastIndexOf('$') + 1) + ":");
        if (clazz.isAnnotationPresent(A1.class)) {
          System.out.print(" A1");
        }
        // The below code will not trigger as A2 is not visible at runtime, but it will ensure the
        // annotation is used.
        if (clazz.isAnnotationPresent(A2.class)) {
          System.out.print(" A2");
        }
        System.out.println();
      }
    }
  }

  @A1
  @A3
  static class C1 {}

  @A2
  @A3
  static class C2 {}

  static class TestClass {

    @UsedByReflection(kind = KeepItemKind.CLASS_AND_METHODS)
    public static void main(String[] args) throws Exception {
      new Reflector().foo(C1.class, C2.class);
    }
  }
}
