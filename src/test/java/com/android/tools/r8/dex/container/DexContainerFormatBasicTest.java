// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.container;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.PartialCompilationTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import java.nio.file.Path;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DexContainerFormatBasicTest extends DexContainerFormatTestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean useContainerDexApiLevel;

  @Parameters(name = "{0}, useContainerDexApiLevel = {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withNoneRuntime().withPartialCompilation().build(),
        BooleanUtils.falseValues());
  }

  private static Path inputA;
  private static Path inputB;

  @BeforeClass
  public static void generateTestApplications() throws Throwable {
    // Build two applications in different packages both with required multidex due to number
    // of methods.
    inputA = getStaticTemp().getRoot().toPath().resolve("application_a.jar");
    inputB = getStaticTemp().getRoot().toPath().resolve("application_b.jar");

    generateApplication(inputA, "a", 10);
    generateApplication(inputB, "b", 10);
  }

  @Test
  public void testNonContainerD8() throws Exception {
    assumeFalse(useContainerDexApiLevel);
    Path outputA =
        testForD8(Backend.DEX, parameters)
            .addProgramFiles(inputA)
            .setMinApi(AndroidApiLevel.L)
            .compile()
            .writeToZip();
    validateDex(outputA, 2, AndroidApiLevel.L.getDexVersion());

    Path outputB =
        testForD8(Backend.DEX, parameters)
            .addProgramFiles(inputB)
            .setMinApi(AndroidApiLevel.L)
            .compile()
            .writeToZip();
    validateDex(outputB, 2, AndroidApiLevel.L.getDexVersion());

    // Merge using D8.
    Path outputMerged =
        testForD8(Backend.DEX, PartialCompilationTestParameters.NONE)
            .addProgramFiles(outputA, outputB)
            .setMinApi(AndroidApiLevel.L)
            .compile()
            .writeToZip();
    validateDex(outputMerged, 4, AndroidApiLevel.L.getDexVersion());
  }

  @Test
  public void testD8ContainerSimpleMerge() throws Exception {
    Path outputFromDexing =
        testForD8(Backend.DEX, parameters)
            .addProgramFiles(inputA)
            .apply(b -> enableContainer(b, useContainerDexApiLevel))
            .compileWithExpectedDiagnostics(
                diagnostics ->
                    checkContainerApiLevelWarning(diagnostics, parameters, useContainerDexApiLevel))
            .writeToZip();
    validateSingleContainerDex(outputFromDexing);

    // Merge using D8.
    Path outputFromMerging =
        testForD8(Backend.DEX, PartialCompilationTestParameters.NONE)
            .addProgramFiles(outputFromDexing)
            .apply(b -> enableContainer(b, useContainerDexApiLevel))
            .compileWithExpectedDiagnostics(
                diagnostics ->
                    checkContainerApiLevelWarning(diagnostics, parameters, useContainerDexApiLevel))
            .writeToZip();
    validateSingleContainerDex(outputFromMerging);

    // Identical DEX after re-merging.
    // TODO(b/414366630): Fix this.
    if (parameters.getPartialCompilationTestParameters().isSome()) {
      assertNotEquals(
          unzipContent(outputFromDexing).get(0).length,
          unzipContent(outputFromMerging).get(0).length);
    } else {
      assertArrayEquals(
          unzipContent(outputFromDexing).get(0), unzipContent(outputFromMerging).get(0));
    }
  }

  @Test
  public void testD8ContainerMoreMerge() throws Exception {
    Path outputA =
        testForD8(Backend.DEX, parameters)
            .addProgramFiles(inputA)
            .apply(b -> enableContainer(b, useContainerDexApiLevel))
            .compileWithExpectedDiagnostics(
                diagnostics ->
                    checkContainerApiLevelWarning(diagnostics, parameters, useContainerDexApiLevel))
            .writeToZip();
    validateSingleContainerDex(outputA);

    Path outputB =
        testForD8(Backend.DEX, parameters)
            .addProgramFiles(inputB)
            .apply(b -> enableContainer(b, useContainerDexApiLevel))
            .compileWithExpectedDiagnostics(
                diagnostics ->
                    checkContainerApiLevelWarning(diagnostics, parameters, useContainerDexApiLevel))
            .writeToZip();
    validateSingleContainerDex(outputB);

    Path outputBoth =
        testForD8(Backend.DEX, parameters)
            .addProgramFiles(inputA, inputB)
            .apply(b -> enableContainer(b, useContainerDexApiLevel))
            .compileWithExpectedDiagnostics(
                diagnostics ->
                    checkContainerApiLevelWarning(diagnostics, parameters, useContainerDexApiLevel))
            .writeToZip();
    validateSingleContainerDex(outputBoth);

    // Merge using D8.
    Path outputMerged =
        testForD8(Backend.DEX, PartialCompilationTestParameters.NONE)
            .addProgramFiles(outputA, outputB)
            .apply(b -> enableContainer(b, useContainerDexApiLevel))
            .compileWithExpectedDiagnostics(
                diagnostics ->
                    checkContainerApiLevelWarning(diagnostics, parameters, useContainerDexApiLevel))
            .writeToZip();
    validateSingleContainerDex(outputMerged);

    // TODO(b/414366630): Fix this.
    if (parameters.getPartialCompilationTestParameters().isSome()) {
      assertNotEquals(
          unzipContent(outputBoth).get(0).length, unzipContent(outputMerged).get(0).length);
    } else {
      assertArrayEquals(unzipContent(outputBoth).get(0), unzipContent(outputMerged).get(0));
    }
  }

  @Test
  public void testR8() throws Exception {
    Path output =
        testForR8(Backend.DEX, parameters)
            .addProgramFiles(inputA)
            .addProgramFiles(inputB)
            .addKeepRules("-keep class * { *; }")
            .apply(b -> enableContainer(b, useContainerDexApiLevel))
            .allowDiagnosticMessages()
            .compileWithExpectedDiagnostics(
                diagnostics ->
                    checkContainerApiLevelWarning(diagnostics, parameters, useContainerDexApiLevel))
            .writeToZip();
    validateSingleContainerDex(output);
  }
}
