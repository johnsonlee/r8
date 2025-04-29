// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.container;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.PartialCompilationTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DexContainerFormatEmptyTest extends DexContainerFormatTestBase {

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

  @Test
  public void testNonContainerD8() throws Exception {
    assumeFalse(useContainerDexApiLevel);

    Path outputA =
        testForD8(Backend.DEX, parameters).setMinApi(AndroidApiLevel.L).compile().writeToZip();
    assertEquals(0, unzipContent(outputA).size());

    Path outputB =
        testForD8(Backend.DEX, parameters).setMinApi(AndroidApiLevel.L).compile().writeToZip();
    assertEquals(0, unzipContent(outputB).size());

    Path outputMerged =
        testForD8(Backend.DEX, PartialCompilationTestParameters.NONE)
            .addProgramFiles(outputA, outputB)
            .setMinApi(AndroidApiLevel.L)
            .compile()
            .writeToZip();
    assertEquals(0, unzipContent(outputMerged).size());
  }

  @Test
  public void testD8Container() throws Exception {
    Path outputFromDexing =
        testForD8(Backend.DEX, parameters)
            .apply(b -> enableContainer(b, useContainerDexApiLevel))
            .compileWithExpectedDiagnostics(
                diagnostics ->
                    checkContainerApiLevelWarning(diagnostics, parameters, useContainerDexApiLevel))
            .writeToZip();
    assertEquals(0, unzipContent(outputFromDexing).size());
  }
}
