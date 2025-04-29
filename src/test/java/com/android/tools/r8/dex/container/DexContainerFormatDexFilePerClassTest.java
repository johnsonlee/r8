// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.container;

import com.android.tools.r8.OutputMode;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.DexVersion;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DexContainerFormatDexFilePerClassTest extends DexContainerFormatTestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean useContainerDexApiLevel;

  @Parameters(name = "{0}, useContainerDexApiLevel = {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withNoneRuntime().build(), BooleanUtils.falseValues());
  }

  @Test
  public void testD8() throws Exception {
    Path outputFromDexing =
        testForD8(Backend.DEX)
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.BAKLAVA))
            .addProgramClasses(A.class, B.class)
            .apply(b -> enableContainer(b, useContainerDexApiLevel))
            .setOutputMode(OutputMode.DexFilePerClass)
            .compileWithExpectedDiagnostics(
                diagnostics ->
                    checkContainerApiLevelWarning(diagnostics, parameters, useContainerDexApiLevel))
            .writeToZip();
    validateDex(
        outputFromDexing,
        // Both lambda and API outline generate a DEX file. No API outline at DEX container API
        // level.
        useContainerDexApiLevel ? 3 : 4,
        // For container DEX API levels non container output use the highest non container format.
        useContainerDexApiLevel
            ? DexVersion.getDexVersion(AndroidApiLevel.V)
            : DexVersion.getDexVersion(AndroidApiLevel.L));
  }

  static class A {
    public void m(List<String> messages) {
      messages.forEach(System.out::println);
    }
  }

  static class B {}
}
