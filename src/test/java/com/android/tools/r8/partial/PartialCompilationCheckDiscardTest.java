// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static com.android.tools.r8.utils.codeinspector.AssertUtils.assertFailsCompilation;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.errors.CheckDiscardDiagnostic;
import com.android.tools.r8.utils.StringDiagnostic;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PartialCompilationCheckDiscardTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void testExcludedClass() throws Exception {
    testForR8Partial(Backend.DEX)
        .addR8ExcludedClasses(CheckDiscardClass.class)
        .addKeepRules("-checkdiscard class " + CheckDiscardClass.class.getTypeName())
        .allowDiagnosticWarningMessages()
        .setMinApi(apiLevelWithNativeMultiDexSupport())
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics
                    .assertOnlyWarnings()
                    .assertWarningsMatch(
                        allOf(
                            diagnosticType(StringDiagnostic.class),
                            diagnosticMessage(
                                containsString(
                                    "matches a class that is excluded from optimization in R8")))));
  }

  @Test
  public void testIncludedClassRemoved() throws Exception {
    testForR8Partial(Backend.DEX)
        .addR8IncludedClasses(CheckDiscardClass.class)
        .addKeepRules("-checkdiscard class " + CheckDiscardClass.class.getTypeName())
        .setMinApi(apiLevelWithNativeMultiDexSupport())
        .compileWithExpectedDiagnostics(TestDiagnosticMessages::assertNoMessages);
  }

  @Test
  public void testIncludedClassKept() throws Exception {
    assertFailsCompilation(
        () ->
            testForR8Partial(Backend.DEX)
                .addR8IncludedClasses(CheckDiscardClass.class)
                .addKeepRules("-checkdiscard class " + CheckDiscardClass.class.getTypeName())
                .addKeepClassRules(CheckDiscardClass.class)
                .setMinApi(apiLevelWithNativeMultiDexSupport())
                .compileWithExpectedDiagnostics(
                    diagnostics ->
                        diagnostics
                            .assertOnlyErrors()
                            .assertErrorsMatch(
                                allOf(
                                    diagnosticType(CheckDiscardDiagnostic.class),
                                    diagnosticMessage(containsString("Discard checks failed."))))));
  }

  static class CheckDiscardClass {}
}
