// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

// This is a reproduction of b/378473616 (extracted from KT-72888)
@RunWith(Parameterized.class)
public class AnnotationImplementedAndInstantiatedInProgramTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static final String EXPECTED_OUTPUT = StringUtils.lines("3", "4", "null");

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .addKeepClassRules(MyAnnotation.class, A.class, B.class)
        .addKeepAttributeAnnotationDefault()
        .addKeepRuntimeVisibleAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Retention(RetentionPolicy.RUNTIME)
  @interface MyAnnotation {
    int x() default 0;
  }

  public static class MyAnnotationImpl implements MyAnnotation {
    private final int x;

    public MyAnnotationImpl(int x) {
      this.x = x + 2;
    }

    @Override
    public int x() {
      return x;
    }

    @Override
    public Class<? extends java.lang.annotation.Annotation> annotationType() {
      return MyAnnotation.class;
    }
  }

  @MyAnnotation(x = 1)
  static class A {}

  @MyAnnotation(x = 2)
  static class B {}

  static class TestClass {
    public static MyAnnotation copyOfMyAnnotation(Class<?> clazz) {
      Object a = clazz.getAnnotation(MyAnnotation.class);
      if (a == null) {
        return null;
      }
      MyAnnotation copy = (MyAnnotation) a;
      return new MyAnnotationImpl(copy.x());
    }

    public static void main(String[] args) {
      MyAnnotation a = copyOfMyAnnotation(A.class);
      System.out.println(a == null ? "null" : a.x());
      a = copyOfMyAnnotation(B.class);
      System.out.println(a == null ? "null" : a.x());
      a = copyOfMyAnnotation(TestClass.class);
      System.out.println(a == null ? "null" : a.x());
    }
  }
}
