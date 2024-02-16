// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
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
import com.android.tools.r8.utils.BooleanUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class UndoConstructorInliningWithSubclassesTest extends TestBase {

  @Parameter(0)
  public boolean neverInlineSubInitializers;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, never inline: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector.assertIsCompleteMergeGroup(A.class, B.class).assertNoOtherClassesMerged())
        .addOptionsModification(
            options -> options.horizontalClassMergerOptions().disableInitialRoundOfClassMerging())
        // When inlining of ASub.<init> and BSub.<init> is allowed, we test that we correctly deal
        // with the following code:
        //   new-instance {v0}, LASub;
        //   invoke-direct {v0}, Ljava/lang/Object;
        //
        // We also test the behavior when ASub.<init> and BSub.<init> are not inlined. In this case,
        // we end up with the following instruction in ASub.<init>:
        //   invoke-direct {v0}, Ljava/lang/Object;
        //
        // In both cases we need to synthesize a constructor to make sure we do not skip a
        // constructor on A or B.
        .applyIf(
            neverInlineSubInitializers,
            b -> b.addKeepRules("-neverinline class **$?Sub { void <init>(); }"))
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoMethodStaticizingAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A", "B");
  }

  static class Main {

    public static void main(String[] args) {
      new ASub().m();
      new BSub().m();
    }
  }

  @NoVerticalClassMerging
  static class A {

    @NeverInline
    @NoMethodStaticizing
    void m() {
      System.out.println("A");
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class ASub extends A {

    ASub() {}
  }

  @NoVerticalClassMerging
  static class B {

    @NeverInline
    @NoMethodStaticizing
    void m() {
      System.out.println("B");
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class BSub extends B {

    BSub() {}
  }
}
