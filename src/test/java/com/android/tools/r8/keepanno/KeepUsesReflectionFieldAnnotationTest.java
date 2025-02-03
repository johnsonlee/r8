// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

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

@RunWith(Parameterized.class)
public class KeepUsesReflectionFieldAnnotationTest extends KeepAnnoTestBase {

  static final String EXPECTED = StringUtils.lines("Hello, world");

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
    return ImmutableList.of(TestClass.class, A.class, B.class);
  }

  private void checkOutput(CodeInspector inspector) {
    if (parameters.isNativeR8()) {
      // A and its field are completely eliminated despite being a precondition.
      assertThat(inspector.clazz(A.class), isAbsent());
    } else {
      // A and its field are soft-pinned in the rules-based extraction.
      assertThat(inspector.clazz(A.class), isPresent());
      assertThat(
          inspector.clazz(A.class).uniqueFieldWithOriginalName("classNameForB"), isPresent());
    }
    assertThat(inspector.clazz(B.class), isPresent());
    assertThat(inspector.clazz(B.class).init(), isPresent());
    assertThat(inspector.clazz(B.class).init("int"), isAbsent());
  }

  static class A {

    @UsesReflection({
      @KeepTarget(classConstant = B.class),
      @KeepTarget(
          classConstant = B.class,
          methodName = "<init>",
          methodParameters = {}),
    })
    public final String classNameForB =
        System.nanoTime() == 0
            ? null
            : "com.android.tools.r8.keepanno.KeepUsesReflectionFieldAnnotationTest$B";

    public B foo() throws Exception {
      return (B) Class.forName(classNameForB).getDeclaredConstructor().newInstance();
    }
  }

  static class B {
    B() {
      // Used.
    }

    B(int unused) {
      // Unused.
    }

    public void bar() {
      System.out.println("Hello, world");
    }
  }

  static class TestClass {

    public static void main(String[] args) throws Exception {
      new A().foo().bar();
    }
  }
}
