// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.attributes;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeepExceptionsAttributeTest extends TestBase {

  private final TestParameters parameters;
  private final String[] EXPECTED = new String[] {"A.foo", "A.bar", "A.foo", "A.bar"};

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public KeepExceptionsAttributeTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .apply(this::addInputs)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .apply(this::addInputs)
        .addKeepMainRule(TestClass.class)
        .addKeepRules(
            StringUtils.lines("-keep class " + typeName(A.class) + " {", "  *** foo(...);", "}"))
        .addKeepAttributes(ProguardKeepAttributes.EXCEPTIONS)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(EXPECTED)
        .inspect(this::inspect);
  }

  private void addInputs(TestBuilder<? extends SingleTestRunResult<?>, ?> builder) {
    builder.addProgramClasses(E1.class, E2.class, I.class, A.class, B.class, TestClass.class);
  }

  private void inspect(CodeInspector inspector) throws NoSuchMethodException {
    assertThat(inspector.clazz(E1.class), isPresent());
    assertThat(inspector.clazz(E2.class), isAbsent());
    assertThat(inspector.clazz(I.class), isPresent());
    assertThat(inspector.clazz(A.class), isPresent());
    assertThat(inspector.clazz(B.class), isPresent());
    {
      MethodSubject aFoo = inspector.method(A.class.getMethod("foo"));
      assertThat(aFoo.getThrowsAnnotation(E1.class), isPresent());
    }
    {
      MethodSubject aBar = inspector.method(A.class.getMethod("bar"));
      assertThat(aBar.getThrowsAnnotation(E2.class), isAbsent());
    }
    {
      MethodSubject aFoobar = inspector.method(A.class.getMethod("foobar"));
      assertThat(aFoobar.getThrowsAnnotation(E1.class), isAbsent());
      assertThat(aFoobar.getThrowsAnnotation(E2.class), isAbsent());
    }
  }

  static class E1 extends Exception {}

  static class E2 extends Exception {}

  interface I {
    void foo() throws E1;

    void bar() throws E2;

    void foobar() throws E1, E2;
  }

  static class A implements I {

    public void foo() throws E1 {
      if (System.nanoTime() < 0) {
        throw new E1();
      }
      System.out.println("A.foo");
    }

    public void bar() throws E2 {
      if (System.nanoTime() < 0) {
        throw new E2();
      }
      System.out.println("A.bar");
    }

    public void foobar() throws E1, E2 {
      foo();
      bar();
    }
  }

  static class B implements I {

    public void foo() throws E1 {
      if (System.nanoTime() < 0) {
        throw new E1();
      }
      System.out.println("B.foo");
    }

    public void bar() throws E2 {
      if (System.nanoTime() < 0) {
        throw new E2();
      }
      System.out.println("B.bar");
    }

    public void foobar() throws E1, E2 {
      foo();
      bar();
    }
  }

  static class TestClass {

    public static void main(String[] args) throws Exception {
      I i = System.nanoTime() > 0 ? new A() : new B();
      i.foo();
      i.bar();
      i.foobar();
    }
  }
}
