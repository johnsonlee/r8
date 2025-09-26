// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.classmerging.vertical;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AnnotationToNonAnnotationClassMergingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepClassAndMembersRules(Main.class)
        .addKeepRuntimeVisibleAnnotations()
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  @I("Hello")
  static class Main {

    public static void main(String[] args) {
      I[] is = new I[] {Main.class.getAnnotation(I.class), new A()};
      print(is);
    }

    static void print(I[] is) {
      for (I i : is) {
        System.out.print(i.value());
      }
      System.out.println();
    }
  }

  @Retention(RetentionPolicy.RUNTIME)
  @interface I {

    String value();
  }

  static class A implements I {

    @Override
    public Class<? extends Annotation> annotationType() {
      return A.class;
    }

    @Override
    public String value() {
      return ", world!";
    }
  }
}
