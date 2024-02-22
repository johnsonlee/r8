// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoAccessModification;
import com.android.tools.r8.NoMethodStaticizing;
import com.android.tools.r8.TestParameters;
import org.junit.Test;

public class ConstructorCantInlineTest extends HorizontalClassMergingTestBase {
  public ConstructorCantInlineTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector.assertIsCompleteMergeGroup(C.class, D.class).assertNoOtherClassesMerged())
        .addVerticallyMergedClassesInspector(
            inspector -> inspector.assertMergedIntoSubtype(A.class).assertNoOtherClassesMerged())
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoAccessModificationAnnotationsForMembers()
        .enableNoMethodStaticizingAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("c", "foo: foo")
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(A.class), isAbsent());
              assertThat(codeInspector.clazz(B.class), isPresent());
              assertThat(codeInspector.clazz(C.class), isPresent());
              assertThat(codeInspector.clazz(D.class), isAbsent());
            });
  }

  public static class A {
    @NoMethodStaticizing
    public String foo() {
      return "foo";
    }
  }

  @NeverClassInline
  public static class B extends A {}

  @NeverClassInline
  public static class C {

    @NeverInline
    @NoAccessModification
    C() {
      System.out.println("c");
    }
  }

  @NeverClassInline
  public static class D {

    @NeverInline
    @NoAccessModification
    D() {
      foo(new B());
    }

    @NeverInline
    @NoAccessModification
    static void foo(A a) {
      System.out.println("foo: " + a.foo());
    }
  }

  public static class Main {
    public static void main(String[] args) {
      new C();
      new D();
    }
  }
}
