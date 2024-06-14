// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.unusedarguments;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
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

@RunWith(Parameterized.class)
public class CollisionWithUnusedArgumentAndParameterAnnotationsTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static final String EXPECTED_OUTPUT = StringUtils.lines("A.<init>(B))", "a B");

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .addKeepRuntimeVisibleParameterAnnotations()
        .addKeepClassAndMembersRules(ParameterAnnotation.class)
        .setMinApi(parameters.getApiLevel())
        .enableNeverClassInliningAnnotations()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8Compat() throws Exception {
    testForR8Compat(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .addKeepRuntimeVisibleParameterAnnotations()
        .addKeepClassAndMembersRules(ParameterAnnotation.class)
        .setMinApi(parameters.getApiLevel())
        .enableNeverClassInliningAnnotations()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.PARAMETER)
  public @interface ParameterAnnotation {}

  static class B {
    public String toString() {
      return "a B";
    }
  }

  @NeverClassInline
  static class A {
    B b;

    // Unused argument is removed, but then an extra argument is added to avoid the signature
    // collision.
    public A(@ParameterAnnotation B b, int unused) {
      this.b = b;
      System.out.println("A.<init>(B, int)");
    }

    public A(@ParameterAnnotation B b) {
      this.b = b;
      System.out.println("A.<init>(B))");
    }

    public String toString() {
      return b.toString();
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(args.length == 0 ? new A(new B()) : new A(new B(), 0));
    }
  }
}
