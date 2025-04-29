// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.container;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.GlobalSyntheticsTestingConsumer;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.apimodel.ApiModelingTestHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.DexVersion;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DexContainerFormatGlobalSyntheticsTest extends DexContainerFormatTestBase {

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

  private void configure(TestCompilerBuilder<?, ?, ?, ?, ?> builder) {
    builder
        .addLibraryFiles(ToolHelper.getAndroidJar(InternalOptions.containerDexApiLevel()))
        .addLibraryClasses(ExceptionNotPresent.class)
        .addProgramClasses(TestClass.class)
        .apply(b -> enableContainer(b, useContainerDexApiLevel))
        .apply(ApiModelingTestHelper::disableGlobalSyntheticCheck)
        .apply(setMockApiLevelForClass(ExceptionNotPresent.class, AndroidApiLevel.MAIN));
  }

  @Test
  public void testD8() throws Exception {
    Path outputFromDexing =
        testForD8(Backend.DEX, parameters)
            .apply(this::configure)
            .compileWithExpectedDiagnostics(
                diagnostics ->
                    checkContainerApiLevelWarning(diagnostics, parameters, useContainerDexApiLevel))
            .inspect(
                inspector -> {
                  ClassSubject globalSyntheticClass = inspector.clazz(ExceptionNotPresent.class);
                  assertThat(globalSyntheticClass, isPresent());
                })
            .writeToZip();
    validateDex(
        outputFromDexing, 1, DexVersion.getDexVersion(InternalOptions.containerDexApiLevel()));
  }

  @Test
  public void testD8Intermediate() throws Exception {
    parameters.assumeNoPartialCompilation();
    GlobalSyntheticsTestingConsumer globals = new GlobalSyntheticsTestingConsumer();
    Path outputFromDexing =
        testForD8(Backend.DEX)
            .apply(this::configure)
            .setIntermediate(true)
            .apply(b -> b.getBuilder().setGlobalSyntheticsConsumer(globals))
            .compileWithExpectedDiagnostics(
                diagnostics ->
                    checkContainerApiLevelWarning(diagnostics, parameters, useContainerDexApiLevel))
            .writeToZip();
    assertTrue(globals.isSingleGlobal());
    validateDex(
        outputFromDexing, 1, DexVersion.getDexVersion(InternalOptions.containerDexApiLevel()));
  }

  @Test
  public void testR8() throws Exception {
    Path outputFromDexing =
        testForR8(Backend.DEX, parameters)
            .apply(this::configure)
            .addKeepClassRules(TestClass.class)
            .allowDiagnosticMessages()
            .compileWithExpectedDiagnostics(
                diagnostics ->
                    checkContainerApiLevelWarning(diagnostics, parameters, useContainerDexApiLevel))
            .writeToZip();
    validateDex(
        outputFromDexing, 1, DexVersion.getDexVersion(InternalOptions.containerDexApiLevel()));
  }

  static class ExceptionNotPresent extends RuntimeException {}

  static class TestClass {

    public static void main(String[] args) {
      try {
        System.out.println("Hello, world!");
      } catch (ExceptionNotPresent e) {
        // Ignore.
      }
    }
  }
}
