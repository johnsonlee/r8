// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.classmerging.horizontal;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoUnusedInterfaceRemoval;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.apimodel.ApiModelingTestHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DefaultInterfaceMethodCollisionWithClassMethodAfterClassMergingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testInterfaceOnProgram() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepClassAndMembersRules(Main.class)
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector
                    .applyIf(
                        !parameters.canUseDefaultAndStaticInterfaceMethods(),
                        i -> i.assertIsCompleteMergeGroup(A.class, B.class))
                    .assertNoOtherClassesMerged())
        .enableInliningAnnotations()
        .enableNoUnusedInterfaceRemovalAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("I", "B");
  }

  @Test
  public void testInterfaceOnLibrary() throws Exception {
    testForR8(parameters)
        .addProgramClasses(A.class, B.class, Main.class)
        .addLibraryClasses(I.class)
        .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
        .addKeepClassAndMembersRules(Main.class)
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector
                    .applyIf(
                        !parameters.canUseDefaultAndStaticInterfaceMethods(),
                        i -> i.assertIsCompleteMergeGroup(A.class, B.class))
                    .assertNoOtherClassesMerged())
        .apply(ApiModelingTestHelper.setMockApiLevelForClass(I.class, AndroidApiLevel.B))
        .apply(
            ApiModelingTestHelper.setMockApiLevelForMethod(
                I.class.getDeclaredMethod("m"), AndroidApiLevel.B))
        .compile()
        .addRunClasspathClasses(I.class)
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            parameters.canUseDefaultAndStaticInterfaceMethods(),
            rr -> rr.assertSuccessWithOutputLines("I", "B"),
            rr -> rr.assertSuccessWithOutputLines("B", "B"));
  }

  static class Main {

    public static void main(String[] args) {
      try {
        test(new A());
      } catch (Exception e) {
        System.out.println("Caught " + e.getClass().getName());
      }
      test(new B());
    }

    // @Keep
    static void test(I i) {
      i.m();
    }
  }

  @NoUnusedInterfaceRemoval
  @NoVerticalClassMerging
  interface I {

    @NeverInline
    default void m() {
      System.out.println("I");
    }
  }

  static class A implements I {}

  static class B implements I {

    @Override
    public void m() {
      System.out.println("B");
    }
  }
}
