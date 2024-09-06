// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.DiagnosticsMatcher;
import com.android.tools.r8.KotlinCompilerTool.KotlinCompiler;
import com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.errors.InterfaceDesugarMissingTypeDiagnostic;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KotlinStdLibCompilationTest extends TestBase {

  private final TestParameters parameters;
  private final KotlinTestParameters kotlinTestParameters;

  @Parameters(name = "{0}, kotlinc: {1}")
  public static List<Object[]> setup() {
    return buildParameters(
        TestParameters.builder().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters()
            .withAllCompilers()
            .withAllLambdaGenerations()
            .withNoTargetVersion()
            .build());
  }

  public KotlinStdLibCompilationTest(
      TestParameters parameters, KotlinTestParameters kotlinTestParameters) {
    this.parameters = parameters;
    this.kotlinTestParameters = kotlinTestParameters;
  }

  @Test
  public void testD8() throws CompilationFailedException {
    parameters.assumeDexRuntime();
    testForD8()
        .addProgramFiles(kotlinTestParameters.getCompiler().getKotlinStdlibJar())
        .setMinApi(parameters)
        .compileWithExpectedDiagnostics(
            diagnostics -> {
              if (kotlinTestParameters.isNewerThanOrEqualTo(KotlinCompilerVersion.KOTLINC_1_8_0)
                  && parameters.isDexRuntime()
                  && parameters.getApiLevel().isLessThan(AndroidApiLevel.N)) {
                // Kotlin stdlib has references to classes introduced in API level 24 and
                // java.lang.AutoCloseable introduced in API level 19.
                diagnostics.assertWarningsCount(
                    kotlinTestParameters.isOlderThanOrEqualTo(KotlinCompilerVersion.KOTLINC_1_9_21)
                        ? 2
                        : (parameters.getApiLevel().isLessThan(AndroidApiLevel.K)) ? 4 : 3);
                diagnostics.assertAllWarningsMatch(
                    DiagnosticsMatcher.diagnosticType(InterfaceDesugarMissingTypeDiagnostic.class));
              } else {
                diagnostics.assertNoMessages();
              }
            });
  }

  @Test
  public void testR8() throws CompilationFailedException {
    KotlinCompiler compiler = kotlinTestParameters.getCompiler();
    testForR8(parameters.getBackend())
        .addProgramFiles(compiler.getKotlinStdlibJar(), compiler.getKotlinAnnotationJar())
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.LATEST))
        .addKeepAllAttributes()
        .addDontObfuscate()
        .addDontShrink()
        .setMode(CompilationMode.DEBUG)
        .setMinApi(parameters)
        .applyIf(
            parameters.isCfRuntime()
                && kotlinTestParameters.isNewerThanOrEqualTo(KotlinCompilerVersion.KOTLINC_1_8_0),
            TestShrinkerBuilder::addDontWarnJavaLangInvokeLambdaMetadataFactory)
        .compile();
  }
}
