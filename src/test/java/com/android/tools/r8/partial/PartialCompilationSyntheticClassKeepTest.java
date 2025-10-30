// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.errors.UnusedProguardKeepRuleDiagnostic;
import com.android.tools.r8.synthesis.SyntheticNaming;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PartialCompilationSyntheticClassKeepTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepRules(
            "-keep class " + I.class.getTypeName() + SyntheticNaming.COMPANION_CLASS_SUFFIX)
        .allowUnusedProguardConfigurationRules()
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics.assertInfosMatch(
                    diagnosticType(UnusedProguardKeepRuleDiagnostic.class)))
        .inspect(inspector -> assertEquals(1, inspector.allClasses().size()))
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            parameters.isDexRuntime() || parameters.getCfRuntime().isNewerThan(CfVm.JDK9),
            rr -> rr.assertSuccessWithOutputLines("Hello, world!"));
  }

  @Test
  public void testR8Partial() throws Exception {
    testForR8Partial(parameters)
        .addR8IncludedClasses(Main.class, I.class)
        .addKeepMainRule(Main.class)
        .addKeepRules(
            "-keep class " + I.class.getTypeName() + SyntheticNaming.COMPANION_CLASS_SUFFIX)
        .allowUnusedProguardConfigurationRules()
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics.assertInfosMatch(
                    diagnosticType(UnusedProguardKeepRuleDiagnostic.class)))
        .inspect(inspector -> assertEquals(1, inspector.allClasses().size()))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  static class Main {

    public static void main(String[] args) {
      I.m();
    }
  }

  interface I {

    static void m() {
      System.out.println("Hello, world!");
    }
  }
}
