// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.annotations;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.annotations.NestedAnnotationsUsedTest.A;
import com.android.tools.r8.annotations.NestedAnnotationsUsedTest.TestClass;
import com.android.tools.r8.utils.StringUtils;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

// This is a regression test for b/359385828.
@RunWith(Parameterized.class)
public class NestedAnnotationsUnusedTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static final String EXPECTED_OUTPUT = StringUtils.lines("Found");

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepClassRules(A.class)
        .addKeepRuntimeVisibleAnnotations()
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .inspect(inspector -> assertThat(inspector.clazz(B.class), isAbsent()))
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.TYPE})
  public @interface A {
    B b();

    B[] bs() default {};
  }

  @Retention(RetentionPolicy.RUNTIME)
  public @interface B {
    String x();
  }

  @A(
      b = @B(x = "1"),
      bs = {@B(x = "2"), @B(x = "3")})
  static class TestClass {

    public static void main(String[] args) {
      System.out.println(TestClass.class.getAnnotation(A.class) != null ? "Found" : "Not found");
    }
  }
}
