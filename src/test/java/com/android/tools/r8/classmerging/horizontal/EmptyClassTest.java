// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import org.junit.Test;

public class EmptyClassTest extends HorizontalClassMergingTestBase {
  public EmptyClassTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector.assertIsCompleteMergeGroup(A.class, B.class).assertNoOtherClassesMerged())
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("a", "b: foo")
        .inspect(codeInspector -> assertThat(codeInspector.clazz(A.class), isPresent()));
  }

  @NeverClassInline
  public static class A {

    @NeverInline
    public A() {
      System.out.println("a");
    }
  }

  @NeverClassInline
  public static class B {

    @NeverInline
    public B(String s) {
      System.out.println("b: " + s);
    }
  }

  public static class Main {
    public static void main(String[] args) {
      A a = new A();
      B b = new B("foo");
    }
  }
}
