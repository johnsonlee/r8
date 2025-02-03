// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.keepanno.annotations.AnnotationPattern;
import com.android.tools.r8.keepanno.annotations.KeepCondition;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.UsedByNative;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

@RunWith(Parameterized.class)
public class KeepUsedByNativeAnnotationTest extends KeepAnnoTestBase {

  static final String EXPECTED = StringUtils.lines("@Anno.value = anno-on-bar", "Hello, world");

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
        .addKeepMainRule(TestClass.class)
        .setExcludedOuterClass(getClass())
        .run(TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .applyIf(parameters.isShrinker(), r -> r.inspect(this::checkOutput));
  }

  public List<Class<?>> getInputClasses() {
    return ImmutableList.of(TestClass.class, Anno.class, A.class, B.class, C.class);
  }

  private void checkOutput(CodeInspector inspector) {
    assertThat(inspector.clazz(A.class), isPresent());
    assertThat(inspector.clazz(B.class), isPresent());
    assertThat(inspector.clazz(C.class), isAbsent());
    assertThat(inspector.clazz(A.class).method("void", "bar"), isPresent());
    assertThat(inspector.clazz(B.class).method("void", "bar"), isPresent());
    assertThat(inspector.clazz(B.class).method("void", "bar", "int"), isAbsent());
  }

  @Retention(RetentionPolicy.RUNTIME)
  private @interface Anno {
    String value();
  }

  @UsedByNative(
      description = "Ensure that the class A remains as we are assuming the contents of its name.",
      preconditions = {@KeepCondition(classConstant = A.class, methodName = "foo")},
      // The kind will default to ONLY_CLASS, so setting this to include members will keep the
      // otherwise unused bar method.
      kind = KeepItemKind.CLASS_AND_MEMBERS)
  static class A {

    public void foo() throws Exception {
      Class<?> clazz = Class.forName(A.class.getTypeName().replace("$A", "$B"));
      Method bar = clazz.getDeclaredMethod("bar");
      Anno annotation = bar.getAnnotation(Anno.class);
      System.out.println("@Anno.value = " + annotation.value());
      bar.invoke(clazz);
    }

    public void bar() {
      // Unused but kept by the annotation.
    }
  }

  static class B {

    @UsedByNative(
        // Only if A.foo is live do we need to keep this.
        preconditions = {@KeepCondition(classConstant = A.class, methodName = "foo")},
        // Both the class and method are reflectively accessed.
        kind = KeepItemKind.CLASS_AND_MEMBERS,
        constrainAnnotations = @AnnotationPattern(constant = Anno.class))
    @Anno("anno-on-bar")
    public static void bar() {
      System.out.println("Hello, world");
    }

    public static void bar(int ignore) {
      throw new RuntimeException("UNUSED");
    }
  }

  static class C {
    // Unused.
  }

  static class TestClass {

    public static void main(String[] args) throws Exception {
      new A().foo();
    }
  }
}
