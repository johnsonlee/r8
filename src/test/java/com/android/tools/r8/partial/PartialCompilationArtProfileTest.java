// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.partial.PartialCompilationWithDefaultInterfaceMethodTest.Main;
import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PartialCompilationArtProfileTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntimes()
        .withApiLevelsStartingAtIncluding(AndroidApiLevel.L)
        .build();
  }

  @Test
  public void test() throws Exception {
    testForR8Partial(parameters.getBackend())
        .addR8IncludedClasses(IncludedInterface.class)
        .addR8ExcludedClasses(Main.class, ExcludedInterface.class)
        .addArtProfileForRewriting(
            ExternalArtProfile.builder()
                .addMethodRule(
                    Reference.methodFromMethod(IncludedInterface.class.getDeclaredMethod("foo")))
                .addMethodRule(
                    Reference.methodFromMethod(ExcludedInterface.class.getDeclaredMethod("bar")))
                .build())
        .setMinApi(parameters)
        .compile()
        .inspectResidualArtProfile(
            (artProfileInspector, codeInspector) -> {
              ClassSubject includedInterfaceClass, excludedInterfaceClass;
              if (parameters.canUseDefaultAndStaticInterfaceMethods()) {
                includedInterfaceClass = codeInspector.clazz(IncludedInterface.class);
                excludedInterfaceClass = codeInspector.clazz(ExcludedInterface.class);
              } else {
                includedInterfaceClass =
                    codeInspector.clazz(
                        SyntheticItemsTestUtils.syntheticCompanionClass(IncludedInterface.class));
                excludedInterfaceClass =
                    codeInspector.clazz(
                        SyntheticItemsTestUtils.syntheticCompanionClass(ExcludedInterface.class));
              }
              artProfileInspector
                  .assertContainsClassRules(includedInterfaceClass, excludedInterfaceClass)
                  .assertContainsMethodRules(
                      includedInterfaceClass.uniqueMethodWithOriginalName("foo"),
                      excludedInterfaceClass.uniqueMethodWithOriginalName("bar"))
                  .assertContainsNoOtherRules();
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("IncludedInterface.foo()", "ExcludedInterface.bar()");
  }

  static class Main {

    public static void main(String[] args) {
      IncludedInterface.foo();
      ExcludedInterface.bar();
    }
  }

  interface IncludedInterface {

    static void foo() {
      System.out.println("IncludedInterface.foo()");
    }
  }

  interface ExcludedInterface {

    static void bar() {
      System.out.println("ExcludedInterface.bar()");
    }
  }
}
