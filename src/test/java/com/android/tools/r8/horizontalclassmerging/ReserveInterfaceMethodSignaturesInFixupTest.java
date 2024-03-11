// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoParameterTypeStrengthening;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.VerticallyMergedClassesInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Regression test for b/328899963. */
@RunWith(Parameterized.class)
public class ReserveInterfaceMethodSignaturesInFixupTest extends TestBase {

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
        .addKeepAttributeLineNumberTable()
        .addKeepAttributeSourceFile()
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector.assertIsCompleteMergeGroup(B.class, C.class).assertNoOtherClassesMerged())
        .addVerticallyMergedClassesInspector(
            VerticallyMergedClassesInspector::assertNoClassesMerged)
        .enableInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoParameterTypeStrengtheningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(AbstractMethodError.class);
  }

  static class Main {

    public static void main(String[] args) {
      I b = System.currentTimeMillis() > 0 ? new BSub() : new CSub();
      b.foo(new CSub());
      I c = System.currentTimeMillis() > 0 ? new CSub() : new BSub();
      c.foo(new CSub());
    }
  }

  @NoVerticalClassMerging
  interface I {

    void foo(C c);
  }

  @NoVerticalClassMerging
  abstract static class A implements I {}

  @NoVerticalClassMerging
  abstract static class B extends A {}

  @NoVerticalClassMerging
  abstract static class C extends A {}

  @NoHorizontalClassMerging
  static class BSub extends B {

    @NeverInline
    @NoParameterTypeStrengthening
    @Override
    public void foo(C c) {
      System.out.println("BSub");
    }
  }

  @NoHorizontalClassMerging
  static class CSub extends C {

    @NeverInline
    @NoParameterTypeStrengthening
    @Override
    public void foo(C c) {
      System.out.println(c);
    }

    @Override
    public String toString() {
      return "CSub";
    }
  }
}
