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
import com.android.tools.r8.partial.PartialCompilationSyntheticMethodKeepTest.Main.NestMember;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PartialCompilationSyntheticMethodKeepTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addProgramClassFileData(getProgramClassFileData())
        .addKeepMainRule(Main.class)
        .addKeepRules(
            "-keepclassmembers class " + NestMember.class.getTypeName() + " { synthetic *; }")
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
        .addProgramClassFileData(getProgramClassFileData())
        .addR8IncludedClasses(false, Main.class, NestMember.class)
        .addKeepMainRule(Main.class)
        .addKeepRules(
            "-keepclassmembers class " + NestMember.class.getTypeName() + " { synthetic *; }")
        .allowUnusedProguardConfigurationRules()
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics.assertInfosMatch(
                    diagnosticType(UnusedProguardKeepRuleDiagnostic.class)))
        .inspect(inspector -> assertEquals(1, inspector.allClasses().size()))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  private static List<byte[]> getProgramClassFileData() throws Exception {
    return ImmutableList.of(
        transformer(Main.class).setNest(Main.class, NestMember.class).transform(),
        transformer(NestMember.class)
            .setNest(Main.class, NestMember.class)
            .setAccessFlags(
                NestMember.class.getDeclaredMethod("greet"),
                flags -> {
                  flags.unsetPublic();
                  flags.setPrivate();
                })
            .transform());
  }

  static class Main {

    public static void main(String[] args) {
      NestMember.greet();
    }

    static class NestMember {

      public static void greet() {
        System.out.println("Hello, world!");
      }
    }
  }
}
