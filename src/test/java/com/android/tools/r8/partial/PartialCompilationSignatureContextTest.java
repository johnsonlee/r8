// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.partial.PartialCompilationSignatureContextTest.ExcludedClass.IncludedClass;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PartialCompilationSignatureContextTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void test() throws Exception {
    testForR8Partial(Backend.DEX)
        .addR8ExcludedClasses(ExcludedClass.class)
        .addR8IncludedClasses(IncludedClass.class)
        .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
        .addKeepAllClassesRule()
        .addKeepAttributeSignature()
        .allowDiagnosticInfoMessages()
        .setMinApi(apiLevelWithNativeMultiDexSupport())
        // TODO(b/415703756): Should not report invalid signature.
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics.assertInfosMatch(
                    diagnosticMessage(containsString("Invalid signature"))));
  }

  static class ExcludedClass<T> {

    abstract class IncludedClass implements Consumer<T> {}
  }
}
