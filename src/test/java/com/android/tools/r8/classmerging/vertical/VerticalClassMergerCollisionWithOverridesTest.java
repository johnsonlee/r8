// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.classmerging.vertical;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class VerticalClassMergerCollisionWithOverridesTest extends TestBase {

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
        .addProgramClasses(A.class, B.class, Main.class)
        .addProgramClassFileData(getTransformedClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("C C.m()", "B B.m()");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(A.class, B.class, Main.class)
        .addProgramClassFileData(getTransformedClass())
        .addKeepMainRule(Main.class)
        .addVerticallyMergedClassesInspector(
            inspector -> inspector.assertMergedIntoSubtype(A.class))
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(AbstractMethodError.class);
  }

  private static byte[] getTransformedClass() throws Exception {
    return transformer(C.class)
        .removeMethods(
            (access, name, descriptor, signature, exceptions) ->
                name.equals("m") && descriptor.equals("()" + descriptor(B.class)))
        .transform();
  }

  static class Main {

    public static void main(String[] args) {
      C c = new C();
      c.callOnA();
      c.callOnB();
    }
  }

  abstract static class A {

    abstract A m();

    A callOnA() {
      return m();
    }
  }

  abstract static class B extends A {

    @NeverInline
    B m() {
      System.out.println("B B.m()");
      return this;
    }

    B callOnB() {
      return m();
    }
  }

  @NeverClassInline
  static class C extends B {

    @NeverInline
    @Override
    C m() {
      System.out.println("C C.m()");
      return this;
    }

    // DELETED BY TRANSFORMER: synthetic bridge B m() { ...}
  }
}
