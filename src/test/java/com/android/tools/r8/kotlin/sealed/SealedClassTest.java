// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.sealed;

import static com.android.tools.r8.KotlinCompilerTool.KOTLINC;
import static com.android.tools.r8.ToolHelper.getFilesInTestFolderRelativeToClass;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SealedClassTest extends TestBase {

  private static final String MAIN = "com.android.tools.r8.kotlin.sealed.kt.FormatKt";
  private static final String[] EXPECTED = new String[] {"ZIP"};

  private final TestParameters parameters;
  private final KotlinTargetVersion targetVersion;

  @Parameters(name = "{0}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), KotlinTargetVersion.values());
  }

  public SealedClassTest(TestParameters parameters, KotlinTargetVersion targetVersion) {
    this.parameters = parameters;
    this.targetVersion = targetVersion;
  }

  private static BiFunction<TestRuntime, KotlinTargetVersion, Path> compilationResults =
      memoizeBiFunction(SealedClassTest::compileKotlinCode);

  private static Path compileKotlinCode(TestRuntime runtime, KotlinTargetVersion targetVersion)
      throws IOException {
    CfRuntime cfRuntime = runtime.isCf() ? runtime.asCf() : TestRuntime.getCheckedInJdk9();
    return kotlinc(cfRuntime, getStaticTemp(), KOTLINC, targetVersion)
        .addSourceFiles(getFilesInTestFolderRelativeToClass(SealedClassTest.class, "kt", ".kt"))
        .compile();
  }

  @Test
  public void testRuntime() throws ExecutionException, CompilationFailedException, IOException {
    testForRuntime(parameters)
        .addProgramFiles(compilationResults.apply(parameters.getRuntime(), targetVersion))
        .addRunClasspathFiles(buildOnDexRuntime(parameters, ToolHelper.getKotlinStdlibJar()))
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws ExecutionException, CompilationFailedException, IOException {
    testForR8(parameters.getBackend())
        .addProgramFiles(compilationResults.apply(parameters.getRuntime(), targetVersion))
        .addProgramFiles(buildOnDexRuntime(parameters, ToolHelper.getKotlinStdlibJar()))
        .setMinApi(parameters.getApiLevel())
        .allowAccessModification()
        .addKeepMainRule(MAIN)
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutputLines(EXPECTED);
  }
}
