// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.classmerging.horizontal;

import static org.junit.Assert.assertEquals;

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
public class HorizontalClassMergerSafeCheckCastPromotionTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().withPartialCompilation().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addHorizontallyMergedClassesInspectorIf(
            !parameters.isRandomPartialCompilation(),
            i -> {
              if (parameters.getPartialCompilationTestParameters().isExcludeAll()) {
                i.assertNoClassesMerged();
              } else {
                i.assertIsCompleteMergeGroup(A.class, B.class).assertNoOtherClassesMerged();
              }
            })
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .compile()
        .inspectIf(
            !parameters.isRandomPartialCompilation(),
            inspector -> assertEquals(2, inspector.allClasses().size()))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A", "A");
  }

  static class Main {

    public static void main(String[] args) {
      I i = System.currentTimeMillis() > 0 ? new A() : new B();
      // Call i.create to ensure I.create cannot be tree shaken.
      System.out.println(i.create());
      i.invoke();
    }
  }

  interface I {

    Object create();

    void invoke();
  }

  @NeverClassInline
  static class A implements I {

    @NeverInline
    @Override
    public Object create() {
      return new A();
    }

    @NeverInline
    @Override
    public void invoke() {
      // Safe down cast.
      A a = (A) create();
      System.out.println(a);
    }

    @Override
    public String toString() {
      return "A";
    }
  }

  @NeverClassInline
  static class B implements I {

    @NeverInline
    @Override
    public Object create() {
      return new B();
    }

    @NeverInline
    @Override
    public void invoke() {
      // Safe down cast.
      B b = (B) create();
      System.out.println(b);
    }
  }
}
