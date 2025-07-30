// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.classmerging.horizontal;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoMethodStaticizing;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class HorizontalClassMergingVirtualMethodMergingWithLibraryTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters)
        .addInnerClasses(getClass())
        .release()
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A.bridgeTarget()", "I.m()");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoMethodStaticizingAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A.bridgeTarget()", "I.m()");
  }

  static class Main {

    public static void main(String[] args) {
      new B().m();
      new D().m();
    }
  }

  @NoVerticalClassMerging
  interface I {

    @NeverInline
    default void m() {
      System.out.println("I.m()");
    }
  }

  static class A {

    @NeverInline
    @NoMethodStaticizing
    public void bridgeTarget() {
      System.out.println("A.bridgeTarget()");
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class B extends A {

    // A candidate for bridge hoisting. Hoisting this bridge changes the resolution of D.m(), since
    // this method will then take precedence over I.m().
    @NeverInline
    public void m() {
      bridgeTarget();
    }
  }

  @NoHorizontalClassMerging
  @NoVerticalClassMerging
  abstract static class C extends A {}

  @NeverClassInline
  static class D extends C implements I {}
}
