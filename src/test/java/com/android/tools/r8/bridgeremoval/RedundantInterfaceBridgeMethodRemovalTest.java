// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.bridgeremoval;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoUnusedInterfaceRemoval;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.MethodAccessFlags;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RedundantInterfaceBridgeMethodRemovalTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .apply(this::configure)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("I", "I");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .apply(this::configure)
        .addKeepMainRule(Main.class)
        .addVerticallyMergedClassesInspector(
            inspector -> inspector.assertMergedIntoSubtype(LSub.class).assertNoOtherClassesMerged())
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoUnusedInterfaceRemovalAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("I", "I");
  }

  private void configure(TestBuilder<?, ?> testBuilder) throws Exception {
    testBuilder
        .addProgramClasses(I.class, K.class, LSub.class, A.class, Main.class)
        // Set the bridge flag so that these methods are subject to removal in the first round of
        // bridge removal.
        .addProgramClassFileData(
            transformer(J.class)
                .setAccessFlags(J.class.getDeclaredMethod("m"), MethodAccessFlags::setBridge)
                .transform(),
            transformer(L.class)
                .setAccessFlags(L.class.getDeclaredMethod("m"), MethodAccessFlags::setBridge)
                .transform());
  }

  static class Main {

    public static void main(String[] args) {
      new A().m();
    }
  }

  @NoHorizontalClassMerging
  @NoUnusedInterfaceRemoval
  @NoVerticalClassMerging
  interface I {

    @NeverInline
    default void m() {
      System.out.println("I");
    }
  }

  @NoHorizontalClassMerging
  @NoUnusedInterfaceRemoval
  @NoVerticalClassMerging
  interface J extends I {

    // When processing this method we will incorrectly memoize that the only invoke-super call in
    // this component is J.super.m() in A.m(). As a result, we incorrectly allow removing L.m() in
    // the first round of redundant interface removal.
    @NeverInline
    @Override
    default void m() {
      I.super.m();
    }
  }

  @NoHorizontalClassMerging
  @NoUnusedInterfaceRemoval
  @NoVerticalClassMerging
  interface K {

    @NeverInline
    default void m() {
      System.out.println("I");
    }
  }

  @NoHorizontalClassMerging
  @NoUnusedInterfaceRemoval
  interface L extends K {

    // Erroneously removed by redundant bridge removal.
    @NeverInline
    @Override
    default void m() {
      K.super.m();
    }
  }

  @NoHorizontalClassMerging
  @NoUnusedInterfaceRemoval
  interface LSub extends L {

    // The invoke-super call to L.super.m() must not be in a subtype of J for the issue to manifest.
    default void callMWithSuperFromLSub() {
      L.super.m();
    }
  }

  @NeverClassInline
  static class A implements J, LSub {

    @Override
    public void m() {
      J.super.m();
      callMWithSuperFromLSub();
    }
  }
}
