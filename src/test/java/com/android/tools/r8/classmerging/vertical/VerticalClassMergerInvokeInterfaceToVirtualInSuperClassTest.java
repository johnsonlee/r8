// Copyright (c) 2023git add, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.classmerging.vertical;

import static com.android.tools.r8.utils.codeinspector.AssertUtils.assertFailsCompilationIf;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class VerticalClassMergerInvokeInterfaceToVirtualInSuperClassTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    assertFailsCompilationIf(
        parameters.isCfRuntime(),
        () ->
            testForR8(parameters.getBackend())
                .addInnerClasses(getClass())
                .addKeepMainRule(Main.class)
                .addVerticallyMergedClassesInspector(
                    inspector -> inspector.assertMergedIntoSubtype(I.class))
                .enableInliningAnnotations()
                .enableNoVerticalClassMergingAnnotations()
                .setMinApi(parameters)
                .compile()
                .run(parameters.getRuntime(), Main.class)
                .applyIf(
                    parameters.getDexRuntimeVersion().isNewerThan(Version.V6_0_1),
                    runResult ->
                        runResult.assertFailureWithErrorThatThrows(
                            IncompatibleClassChangeError.class),
                    runResult ->
                        runResult.assertFailureWithErrorThatThrows(NoSuchMethodError.class)));
  }

  static class Main {

    public static void main(String[] args) {
      I i = new B();
      i.m();
    }
  }

  interface I {

    void m();
  }

  @NoVerticalClassMerging
  static class A {

    @NeverInline
    public void m() {
      System.out.println("Hello, world!");
    }
  }

  static class B extends A implements I {}
}
