// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal.dispatch;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoMethodStaticizing;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.classmerging.horizontal.HorizontalClassMergingTestBase;
import org.junit.Test;

public class SuperMethodMergedTest extends HorizontalClassMergingTestBase {
  public SuperMethodMergedTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(this.getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoMethodStaticizingAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("foo", "parent b", "parent b", "x", "parent b")
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(ParentA.class), isPresent());
              assertThat(codeInspector.clazz(ParentB.class), isAbsent());
              assertThat(codeInspector.clazz(X.class), isPresent());
              assertThat(codeInspector.clazz(Y.class), isAbsent());
            });
  }

  @NeverClassInline
  public static class ParentA {

    @NeverInline
    @NoMethodStaticizing
    void foo() {
      System.out.println("foo");
    }
  }

  @NoVerticalClassMerging
  @NeverClassInline
  public static class ParentB {

    @NeverInline
    @NoMethodStaticizing
    void print() {
      System.out.println("parent b");
    }
  }

  @NeverClassInline
  public static class X extends ParentB {

    public X() {
      print();
    }

    @NeverInline
    @NoMethodStaticizing
    @Override
    void print() {
      super.print();
      System.out.println("x");
    }
  }

  @NeverClassInline
  public static class Y extends ParentB {

    public Y() {
      print();
    }
  }

  public static class Main {
    public static void main(String[] args) {
      ParentA a = new ParentA();
      a.foo();
      ParentB b = new ParentB();
      b.print();
      X x = new X();
      Y y = new Y();
    }
  }
}
