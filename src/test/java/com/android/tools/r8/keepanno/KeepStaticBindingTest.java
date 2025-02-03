// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.keepanno.annotations.KeepBinding;
import com.android.tools.r8.keepanno.annotations.KeepEdge;
import com.android.tools.r8.keepanno.annotations.KeepTarget;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

@RunWith(Parameterized.class)
public class KeepStaticBindingTest extends KeepAnnoTestBase {

  static final String EXPECTED = StringUtils.lines("Hello!");

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
    return ImmutableList.of(TestClass.class, Unused.class);
  }

  private void checkOutput(CodeInspector inspector) {
    assertThat(inspector.clazz(TestClass.class), isPresent());
    assertThat(inspector.clazz(TestClass.class).mainMethod(), isPresent());
    assertThat(inspector.clazz(Unused.class), isAbsent());
  }

  /**
   * This conditional rule expresses that if any class in the program has a live "bar" method then
   * that same classes "foo" method is to be kept. The binding(s) establishes the relation between
   * the holder of the two methods.
   */
  @KeepEdge(
      bindings = {
        @KeepBinding(
            bindingName = "ClassWithMain",
            methodName = "main",
            methodReturnType = "void",
            methodParameters = {"java.lang.String[]"})
      },
      consequences = {
        @KeepTarget(classFromBinding = "ClassWithMain"),
        @KeepTarget(memberFromBinding = "ClassWithMain")
      })
  static class TestClass {

    public static void main(String[] args) {
      System.out.println("Hello!");
    }
  }

  static class Unused {
    // No matching main method, so should not match the rule.

    public static void main() {
      // Non-matching main.
    }
  }
}
