// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jdk17.records.dex.container;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.GlobalSyntheticsTestingConsumer;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.dex.container.DexContainerFormatTestBase;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.DexVersion;
import com.android.tools.r8.utils.InternalOptions;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DexContainerFormatRecordTest extends DexContainerFormatTestBase {

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
  public void testD8() throws Exception {
    Path outputFromDexing =
        testForD8(Backend.DEX, parameters)
            .addInnerClassesAndStrippedOuter(getClass())
            .apply(b -> enableContainer(b, useContainerDexApiLevel))
            .compileWithExpectedDiagnostics(
                diagnostics ->
                    checkContainerApiLevelWarning(diagnostics, parameters, useContainerDexApiLevel))
            .writeToZip();
    validateDex(
        outputFromDexing, 1, DexVersion.getDexVersion(InternalOptions.containerDexApiLevel()));
  }

  @Test
  public void testD8Intermediate() throws Exception {
    assumeTrue(parameters.getPartialCompilationTestParameters().isNone());
    GlobalSyntheticsTestingConsumer globals = new GlobalSyntheticsTestingConsumer();
    Path outputFromDexing =
        testForD8(Backend.DEX)
            .addInnerClassesAndStrippedOuter(getClass())
            .apply(b -> enableContainer(b, useContainerDexApiLevel))
            .setIntermediate(true)
            // This is an artificial setup, as records is supported for the API level where
            // container format was introduced.
            .applyIf(
                !useContainerDexApiLevel, b -> b.getBuilder().setGlobalSyntheticsConsumer(globals))
            .compileWithExpectedDiagnostics(
                diagnostics ->
                    checkContainerApiLevelWarning(diagnostics, parameters, useContainerDexApiLevel))
            .writeToZip();
    if (!useContainerDexApiLevel) {
      assertTrue(globals.isSingleGlobal());
    }
    validateDex(
        outputFromDexing, 1, DexVersion.getDexVersion(InternalOptions.containerDexApiLevel()));
  }

  @Test
  public void testR8() throws Exception {
    Path outputFromDexing =
        testForR8(Backend.DEX, parameters)
            .addInnerClassesAndStrippedOuter(getClass())
            .addKeepClassRules(Record.class)
            .apply(b -> enableContainer(b, useContainerDexApiLevel))
            .allowDiagnosticMessages()
            .compileWithExpectedDiagnostics(
                diagnostics ->
                    checkContainerApiLevelWarning(diagnostics, parameters, useContainerDexApiLevel))
            .writeToZip();
    validateDex(
        outputFromDexing, 1, DexVersion.getDexVersion(InternalOptions.containerDexApiLevel()));
  }

  record Record() {}
}
