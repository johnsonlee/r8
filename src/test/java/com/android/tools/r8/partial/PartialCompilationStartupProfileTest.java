// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsentIf;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.errors.StartupClassesNonStartupFractionDiagnostic;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.startup.profile.ExternalStartupMethod;
import com.android.tools.r8.startup.utils.StartupTestingUtils;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PartialCompilationStartupProfileTest extends TestBase {

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
        .allowDiagnosticInfoMessages()
        .apply(
            testBuilder -> StartupTestingUtils.addStartupProfile(testBuilder, getStartupProfile()))
        .setMinApi(parameters)
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics
                    .assertInfosMatch(
                        diagnosticType(StartupClassesNonStartupFractionDiagnostic.class))
                    .assertNoWarnings()
                    .assertNoErrors())
        .inspectMultiDex(
            primaryDexInspector -> {
              assertEquals(2, primaryDexInspector.allClasses().size());
              ClassSubject includedInterfaceClass, excludedInterfaceClass;
              if (parameters.canUseDefaultAndStaticInterfaceMethods()) {
                includedInterfaceClass = primaryDexInspector.clazz(IncludedInterface.class);
                excludedInterfaceClass = primaryDexInspector.clazz(ExcludedInterface.class);
              } else {
                includedInterfaceClass =
                    primaryDexInspector.clazz(
                        SyntheticItemsTestUtils.syntheticCompanionClass(IncludedInterface.class));
                excludedInterfaceClass =
                    primaryDexInspector.clazz(
                        SyntheticItemsTestUtils.syntheticCompanionClass(ExcludedInterface.class));
              }
              assertThat(includedInterfaceClass, isPresent());
              assertThat(excludedInterfaceClass, isPresent());
            },
            secondaryDexInspector -> {
              assertEquals(
                  parameters.canUseDefaultAndStaticInterfaceMethods() ? 1 : 2,
                  secondaryDexInspector.allClasses().size());
              assertThat(secondaryDexInspector.clazz(Main.class), isPresent());
              assertThat(
                  secondaryDexInspector.clazz(ExcludedInterface.class),
                  isAbsentIf(parameters.canUseDefaultAndStaticInterfaceMethods()));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("IncludedInterface.foo()", "ExcludedInterface.bar()");
  }

  private static List<ExternalStartupMethod> getStartupProfile() throws NoSuchMethodException {
    return ImmutableList.of(
        ExternalStartupMethod.builder()
            .setMethodReference(
                Reference.methodFromMethod(IncludedInterface.class.getDeclaredMethod("foo")))
            .build(),
        ExternalStartupMethod.builder()
            .setMethodReference(
                Reference.methodFromMethod(ExcludedInterface.class.getDeclaredMethod("bar")))
            .build());
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
