// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentIf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.keepanno.annotations.KeepTarget;
import com.android.tools.r8.keepanno.annotations.UsesReflection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeepUsesReflectionAnnotationTest extends KeepAnnoTestBase {

  static final String EXPECTED = StringUtils.lines("Hello, world");

  @Parameter public KeepAnnoParameters parameters;

  @Parameters(name = "{0}")
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
        .allowUnusedProguardConfigurationRules()
        .run(TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .applyIf(
            parameters.isShrinker(), r -> r.inspect(inspector -> checkOutput(inspector, false)));
  }

  @Test
  public void testUsesReflectionInD8OfR8Partial() throws Exception {
    assumeTrue(parameters.isR8Partial());
    testForKeepAnno(parameters)
        .addProgramClasses(getInputClasses())
        .applyIfR8Partial(
            builder ->
                builder
                    .clearR8PartialConfiguration()
                    .setR8PartialConfiguration(b -> b.includeAll().excludeClasses(A.class)))
        .addKeepMainRule(TestClass.class)
        .run(TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .applyIf(
            parameters.isShrinker(), r -> r.inspect(inspector -> checkOutput(inspector, true)));
  }

  public List<Class<?>> getInputClasses() {
    return ImmutableList.of(TestClass.class, A.class, B.class, C.class);
  }

  private void checkOutput(CodeInspector inspector, boolean aOnClasspath) {
    assertThat(inspector.clazz(A.class), isPresent());
    assertThat(inspector.clazz(B.class), isPresent());
    assertThat(inspector.clazz(C.class), isPresentIf(parameters.isPG() || aOnClasspath));
    assertThat(inspector.clazz(B.class).method("void", "bar"), isPresent());
    assertThat(inspector.clazz(B.class).method("void", "bar", "int"), isAbsent());
  }

  static class A {

    @UsesReflection({
      // Ensure that the class A remains as we are assuming the contents of its name.
      @KeepTarget(classConstant = A.class),
      // Ensure that the class B remains as we are looking it up by reflected name.
      @KeepTarget(classConstant = B.class),
      // Ensure the method 'bar' remains as we are invoking it by reflected name.
      @KeepTarget(
          classConstant = B.class,
          methodName = "bar",
          methodParameters = {},
          methodReturnType = "void")
    })
    public void foo() throws Exception {
      Class<?> clazz = Class.forName(A.class.getTypeName().replace("$A", "$B"));
      clazz.getDeclaredMethod("bar").invoke(clazz);
    }

    // This annotation is not active as its implicit precondition "void A.foo(int)" is not used.
    @UsesReflection({@KeepTarget(classConstant = C.class)})
    public void foo(int unused) {
      // Unused.
    }
  }

  static class B {
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
