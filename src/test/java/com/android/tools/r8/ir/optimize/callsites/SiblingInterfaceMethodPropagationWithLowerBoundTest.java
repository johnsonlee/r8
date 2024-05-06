// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.callsites;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoUnusedInterfaceRemoval;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Reproduction of issue found in fastutil where argument information qualified by lower bound type
 * information is incorrectly propagated to classes below the lower bound in the interface method
 * argument propagator.
 */
@RunWith(Parameterized.class)
public class SiblingInterfaceMethodPropagationWithLowerBoundTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoUnusedInterfaceRemovalAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject bClassSubject = inspector.clazz(B.class);
              assertThat(bClassSubject, isPresent());

              MethodSubject bMethodSubject = bClassSubject.uniqueMethodWithOriginalName("m");
              assertThat(bMethodSubject, isPresent());
              assertTrue(bMethodSubject.streamInstructions().anyMatch(i -> i.isConstNumber(2)));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccess();
  }

  static class Main {

    public static void main(String[] args) {
      // To prevent I.m() from being removed by tree shaking.
      I i = System.currentTimeMillis() > 0 ? new E() : new F();
      i.m(2);
      new A().m(1);
      new C().m(2);
    }
  }

  @NoHorizontalClassMerging
  @NoUnusedInterfaceRemoval
  @NoVerticalClassMerging
  interface I {

    void m(int i);
  }

  @NoHorizontalClassMerging
  @NoUnusedInterfaceRemoval
  @NoVerticalClassMerging
  interface J extends I {}

  @NeverClassInline
  @NoHorizontalClassMerging
  @NoVerticalClassMerging
  static class A implements I {

    @NeverInline
    @Override
    public void m(int i) {
      System.out.println(i);
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  @NoVerticalClassMerging
  static class B extends A {

    @NeverInline
    @Override
    public void m(int i) {
      System.out.println(i);
    }
  }

  @NeverClassInline
  @NoVerticalClassMerging
  static class C extends B implements J {}

  @NeverClassInline
  @NoHorizontalClassMerging
  @NoVerticalClassMerging
  static class E implements I {

    @Override
    public void m(int i) {
      System.out.println(i);
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  @NoVerticalClassMerging
  static class F implements I {

    @Override
    public void m(int i) {
      System.out.println(i);
    }
  }
}
