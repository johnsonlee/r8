// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import org.junit.Test;

// Similar to ClassesDistinguishedByDirectCheckCastTest, but where the check-cast is guarded by a
// catch handler block.
public class ClassesDistinguishedByDirectCheckCastCatchHandlerTest
    extends HorizontalClassMergingTestBase {

  public ClassesDistinguishedByDirectCheckCastCatchHandlerTest(TestParameters parameters) {
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
        .assertSuccessWithOutputLines("fail", "bar");
  }

  @NeverClassInline
  public static class A {
    @NeverInline
    public void foo() {
      System.out.println("foo");
    }
  }

  @NeverClassInline
  public static class B {
    @NeverInline
    public void bar() {
      System.out.println("bar");
    }
  }

  public static class Main {
    @NeverInline
    public static void checkObject(Object o) {
      try {
        B b = (B) o;
        b.bar();
      } catch (ClassCastException ex) {
        System.out.println("fail");
      }
    }

    public static void main(String[] args) {
      A a = new A();
      B b = new B();
      checkObject(a);
      checkObject(b);
    }
  }
}
