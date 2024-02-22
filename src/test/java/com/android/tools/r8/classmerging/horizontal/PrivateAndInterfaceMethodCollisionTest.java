// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoMethodStaticizing;
import com.android.tools.r8.NoUnusedInterfaceRemoval;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestParameters;
import org.junit.Test;

public class PrivateAndInterfaceMethodCollisionTest extends HorizontalClassMergingTestBase {

  public PrivateAndInterfaceMethodCollisionTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void test() throws Exception {
    parameters.assumeDexRuntime();
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        // With default method desugaring all uses of J::foo are eliminated so A and B merge.
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector
                    .applyIf(
                        !parameters.canUseDefaultAndStaticInterfaceMethods(),
                        i -> i.assertIsCompleteMergeGroup(A.class, B.class))
                    .assertNoOtherClassesMerged())
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoMethodStaticizingAnnotations()
        .enableNoUnusedInterfaceRemovalAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A", "A.foo()", "B.bar()", "C", "J.foo()");
  }

  static class Main {

    public static void main(String[] args) {
      new A().foo();
      new B().bar();
      new C().foo();
    }
  }

  @NoUnusedInterfaceRemoval
  @NoVerticalClassMerging
  interface J {

    @NeverInline
    @NoMethodStaticizing
    default void foo() {
      System.out.println("J.foo()");
    }
  }

  @NeverClassInline
  static class A {

    @NeverInline
    A() {
      System.out.println("A");
    }

    @NeverInline
    @NoMethodStaticizing
    private void foo() {
      System.out.println("A.foo()");
    }
  }

  @NeverClassInline
  @NoVerticalClassMerging
  static class B {

    // Only here to make sure that B is not made abstract as a result of tree shaking.
    @NeverInline
    @NoMethodStaticizing
    public void bar() {
      System.out.println("B.bar()");
    }
  }

  @NeverClassInline
  static class C extends B implements J {

    @NeverInline
    C() {
      System.out.println("C");
    }
  }
}
