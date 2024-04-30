// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.callsites;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoParameterTypeStrengthening;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Reproduction of b/336791970 where we incorrectly mark A.m() as a monomorphic method meaning that
 * the argument "B" in the call that resolves to A.m() is not propagated to B.m().
 */
@RunWith(Parameterized.class)
public class PropagationFromBaseMethodWithSingleTargetTest extends TestBase {

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
        .assertSuccessWithOutputLines("B", "C");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoParameterTypeStrengtheningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/336791970): Should succeed.
        .assertFailureWithErrorThatThrows(NullPointerException.class);
  }

  static class Main {

    public static void main(String[] args) {
      A a = System.currentTimeMillis() > 0 ? new B() : new A();
      a.m("B");
      call(new C());
    }

    @NeverInline
    @NoParameterTypeStrengthening
    static void call(I i) {
      i.m("C");
    }
  }

  @NoVerticalClassMerging
  interface I {

    void m(String s);
  }

  static class A {

    public void m(String s) {
      System.out.println(s);
    }
  }

  @NoHorizontalClassMerging
  static class B extends A {

    @Override
    public void m(String s) {
      System.out.println(s);
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class C extends A implements I {}
}
