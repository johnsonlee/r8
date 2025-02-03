// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.keepanno.annotations.KeepCondition;
import com.android.tools.r8.keepanno.annotations.KeepConstraint;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.UsedByReflection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KeepUsedByReflectionAnnotationTest extends KeepAnnoTestBase {

  static final String EXPECTED = StringUtils.lines("Hello, world");

  private final KeepAnnoParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static List<KeepAnnoParameters> data() {
    return createParameters(
        getTestParameters().withDefaultRuntimes().withMaximumApiLevel().build());
  }

  public KeepUsedByReflectionAnnotationTest(KeepAnnoParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    Class<?> mainClass = TestClass.class;
    testForKeepAnno(parameters)
        .addProgramClasses(getInputClasses())
        .addKeepMainRule(mainClass)
        .setExcludedOuterClass(getClass())
        .run(mainClass)
        .assertSuccessWithOutput(EXPECTED)
        .applyIf(parameters.isShrinker(), r -> r.inspect(this::checkOutput));
  }

  @Test
  public void testNoRef() throws Exception {
    Class<?> mainClass = TestClassNoRef.class;
    testForKeepAnno(parameters)
        .addProgramClasses(getInputClasses())
        .addKeepMainRule(mainClass)
        .allowUnusedProguardConfigurationRules()
        .setExcludedOuterClass(getClass())
        .run(mainClass)
        .assertSuccessWithOutput(EXPECTED)
        .applyIf(parameters.isR8(), r -> r.inspect(this::checkOutputNoRef))
        // PG does not eliminate B so the same output remains.
        .applyIf(parameters.isPG(), r -> r.inspect(this::checkOutput));
  }

  private List<Class<?>> getInputClasses() {
    return ImmutableList.of(TestClass.class, TestClassNoRef.class, A.class, B.class, C.class);
  }

  private void checkOutput(CodeInspector inspector) {
    assertThat(inspector.clazz(A.class), isPresent());
    assertThat(inspector.clazz(B.class), isPresent());
    assertThat(inspector.clazz(C.class), isAbsent());
    assertThat(inspector.clazz(B.class).method("void", "bar"), isPresent());
    assertThat(inspector.clazz(B.class).method("void", "bar", "int"), isAbsent());
  }

  private void checkOutputNoRef(CodeInspector inspector) {
    // A remains as it has an unconditional keep annotation.
    assertThat(inspector.clazz(A.class), isPresent());
    // B should be inlined and eliminated since A.foo is not live and its keep annotation inactive.
    assertThat(inspector.clazz(B.class), isAbsent());
    assertThat(inspector.clazz(C.class), isAbsent());
  }

  @UsedByReflection(
      description = "Ensure that A remains valid for lookup as we compute B's name from it.",
      constraints = {KeepConstraint.LOOKUP, KeepConstraint.NAME})
  static class A {

    public void foo() throws Exception {
      Class<?> clazz = Class.forName(A.class.getTypeName().replace("$A", "$B"));
      clazz.getDeclaredMethod("bar").invoke(clazz);
    }
  }

  static class B {

    @UsedByReflection(
        // Only if A.foo is live do we need to keep this.
        preconditions = {@KeepCondition(classConstant = A.class, methodName = "foo")},
        // Both the class and method are reflectively accessed.
        kind = KeepItemKind.CLASS_AND_METHODS,
        // Both the class and method need to be looked up. Since static, only the method is invoked.
        constraints = {KeepConstraint.LOOKUP, KeepConstraint.NAME, KeepConstraint.METHOD_INVOKE})
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

  static class TestClassNoRef {

    public static void main(String[] args) throws Exception {
      B.bar();
    }
  }
}
