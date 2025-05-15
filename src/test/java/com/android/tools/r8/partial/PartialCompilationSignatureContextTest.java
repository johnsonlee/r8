// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
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

// Regression test for b/415703756.
@RunWith(Parameterized.class)
public class PartialCompilationSignatureContextTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void testR8() throws Exception {
    testForR8(Backend.DEX)
        .addProgramClasses(IncludedClass.class)
        .addClasspathClasses(ExcludedClass.class)
        .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
        .addKeepAllClassesRule()
        .addKeepAttributeSignature()
        .setMinApi(apiLevelWithNativeMultiDexSupport())
        .compileWithExpectedDiagnostics(TestDiagnosticMessages::assertNoMessages);
  }

  @Test
  public void testR8Partial() throws Exception {
    testForR8Partial(Backend.DEX)
        .addR8ExcludedClasses(ExcludedClass.class)
        .addR8IncludedClasses(IncludedClass.class)
        .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
        .addKeepAllClassesRule()
        .addKeepAttributeSignature()
        .setMinApi(apiLevelWithNativeMultiDexSupport())
        .compileWithExpectedDiagnostics(TestDiagnosticMessages::assertNoMessages);
  }

  static class ExcludedClass<T> {

    abstract class IncludedClass implements Consumer<T> {}
  }
}
