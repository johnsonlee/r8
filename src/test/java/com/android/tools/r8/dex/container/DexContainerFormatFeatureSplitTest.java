// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.container;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.InternalOptions;
import java.nio.file.Path;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DexContainerFormatFeatureSplitTest extends DexContainerFormatTestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean useContainerDexApiLevel;

  @Parameters(name = "{0}, useContainerDexApiLevel = {1}")
  public static List<Object[]> data() {
    return buildParameters(getTestParameters().withNoneRuntime().build(), BooleanUtils.values());
  }

  private static Path inputBase;
  private static Path inputFeature1;
  private static Path inputFeature2;

  @BeforeClass
  public static void generateTestApplications() throws Throwable {
    // Build two applications in different packages both with required multidex due to number
    // of methods.
    inputBase = getStaticTemp().getRoot().toPath().resolve("application.jar");
    inputFeature1 = getStaticTemp().getRoot().toPath().resolve("application_1.jar");
    inputFeature2 = getStaticTemp().getRoot().toPath().resolve("application_2.jar");

    generateApplication(inputBase, "base", 10);
    generateApplication(inputFeature1, "feature1", 10);
    generateApplication(inputFeature2, "feature2", 10);
  }

  @Test
  public void test() throws Exception {
    R8TestCompileResult result =
        testForR8(Backend.DEX)
            .addProgramFiles(inputBase)
            .addFeatureSplit(inputFeature1)
            .addFeatureSplit(inputFeature2)
            .addKeepRules(
                "-keep class base.** { *; }",
                "-keep class feature1.** { *; }",
                "-keep class feature2.** { *; }")
            .apply(b -> enableContainer(b, useContainerDexApiLevel))
            .allowDiagnosticMessages()
            .compileWithExpectedDiagnostics(
                diagnostics -> checkContainerApiLevelWarning(diagnostics, useContainerDexApiLevel));
    Path basePath = result.writeToZip();
    Path feature1Path = result.getFeature(0);
    Path feature2Path = result.getFeature(1);

    validateDex(basePath, 1, InternalOptions.containerDexApiLevel().getDexVersion());
    validateDex(feature1Path, 1, InternalOptions.containerDexApiLevel().getDexVersion());
    validateDex(feature2Path, 1, InternalOptions.containerDexApiLevel().getDexVersion());
  }
}
