// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.ifrule;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PinningStarPatternTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultDexRuntime().build();
  }

  public static final List<Class<?>> EXPECTED_ABSENT = ImmutableList.of(A.class);
  public static final List<Class<?>> EXPECTED_PRESENT = ImmutableList.of(TestClass.class, B.class);

  private TestParameters parameters;

  public PinningStarPatternTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testNoKeep() throws Exception {
    testKeepRule("", ImmutableList.of(A.class, B.class), ImmutableList.of(TestClass.class));
  }

  @Test
  public void testNoIf() throws Exception {
    testKeepRule("-keep class **B { *; }", EXPECTED_ABSENT, EXPECTED_PRESENT);
  }

  @Test
  public void testR8IfStar() throws Exception {
    // TODO(b/316100042): We should not keep A.class.
    testKeepRule(
        "-if class * -keep class **B { *; }",
        ImmutableList.of(),
        ImmutableList.of(B.class, TestClass.class, A.class));
  }

  @Test
  public void testR8IfStarWithMethod() throws Exception {
    // TODO(b/316100042): We should not keep A.class.
    testKeepRule(
        "-if class * { z(); } -keep class **B { *; }",
        ImmutableList.of(),
        ImmutableList.of(B.class, TestClass.class, A.class));
  }

  @Test
  public void testR8IfStarWithEmptyMethod() throws Exception {
    // TODO(b/316100042): We should not keep A.class.
    // We should also not keep B, since y() is dead (empty).
    testKeepRule(
        "-if class * { y(); } -keep class **B { *; }",
        ImmutableList.of(),
        ImmutableList.of(B.class, TestClass.class, A.class));
  }

  @Test
  public void testR8IfStarField() throws Exception {
    // TODO(b/316100042): We should not keep A.class.
    // We should also not keep B, since the only_read field is dead.
    testKeepRule(
        "-if class * { int only_read; } -keep class **B { *; }",
        ImmutableList.of(),
        ImmutableList.of(B.class, TestClass.class, A.class));
  }

  private void testKeepRule(String keepRule, List<Class<?>> absent, List<Class<?>> present)
      throws IOException, ExecutionException, CompilationFailedException {
    testForR8(parameters.getBackend())
        .addProgramClasses(A.class, B.class, TestClass.class)
        .addKeepRules(keepRule)
        .addKeepMainRule(TestClass.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("42", "0")
        .inspect(
            inspector -> {
              absent.forEach(clazz -> assertThat(inspector.clazz(clazz), isAbsent()));
              present.forEach(clazz -> assertThat(inspector.clazz(clazz), isPresent()));
            });
  }

  static class A {
    boolean use = true;

    public A() {
      if (System.currentTimeMillis() == 0) {
        use = false;
      }
    }

    public void bar() {
      if (use) {
        System.out.println(42);
      }
    }
  }

  static class B {
    public static void foo() {
      System.out.println(0);
    }
  }

  static class TestClass {

    public static int only_read = 88;

    public void z() {
      if (System.currentTimeMillis() == 0) {
        System.out.println("foobar");
      }
    }

    public void y() {}

    public static void main(String[] args) {
      new TestClass().z();
      new TestClass().y();
      int a = TestClass.only_read;
      new A().bar();
      B.foo();
    }
  }
}
