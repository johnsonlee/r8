// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.verifyThat;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.apimodel.another_extension.AnotherExtensionApiLibraryClass;
import com.android.tools.r8.apimodel.extension.ExtensionApiLibraryClass;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelOutlineMethodExtensionApiMultipleExtensionPackagesTest extends TestBase {

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines(
          "ExtensionApiLibraryClass::extensionApi",
          "AnotherExtensionApiLibraryClass::extensionApi");

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean useExtensionJar;

  @Parameters(name = "{0}, useExtensionJar = {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  private static final List<Class<?>> extensionLibraryClasses =
      ImmutableList.of(ExtensionApiLibraryClass.class, AnotherExtensionApiLibraryClass.class);
  private static Path extensionLibraryJar;

  @BeforeClass
  public static void setUp() throws Exception {
    extensionLibraryJar =
        zipWithTestClasses(
            getStaticTemp().newFolder().toPath().resolve("extension.jar"), extensionLibraryClasses);
  }

  private void setupTestBuilder(TestCompilerBuilder<?, ?, ?, ?, ?> testBuilder) throws Exception {
    testBuilder
        .addLibraryFiles(extensionLibraryJar)
        .addDefaultRuntimeLibrary(parameters)
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .addOptionsModification(
            options -> {
              if (useExtensionJar) {
                options.apiModelingOptions().androidApiExtensionLibraries =
                    extensionLibraryJar.toString();
              } else {
                String typeName = ExtensionApiLibraryClass.class.getTypeName();
                String anotherTypeName = AnotherExtensionApiLibraryClass.class.getTypeName();
                options.apiModelingOptions().androidApiExtensionPackages =
                    typeName.substring(0, typeName.lastIndexOf('.'))
                        + ','
                        + anotherTypeName.substring(0, anotherTypeName.lastIndexOf('.'))
                        + ','
                        + typeName.substring(0, typeName.lastIndexOf('.'));
              }
            })
        // TODO(b/213552119): Remove when enabled by default.
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        .apply(ApiModelingTestHelper::enableOutliningOfMethods)
        .apply(ApiModelingTestHelper::disableStubbingOfClasses);
  }

  private void populateBootClasspath(TestCompileResult<?, ?> compilerResult) throws Exception {
    compilerResult.addBootClasspathClasses(extensionLibraryClasses);
  }

  @Test
  public void testD8Debug() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .setMode(CompilationMode.DEBUG)
        .apply(this::setupTestBuilder)
        .compile()
        .apply(this::populateBootClasspath)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT)
        .inspect(this::inspect);
  }

  @Test
  public void testD8Release() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .setMode(CompilationMode.RELEASE)
        .apply(this::setupTestBuilder)
        .compile()
        .apply(this::populateBootClasspath)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT)
        .inspect(this::inspect);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .apply(this::setupTestBuilder)
        .addKeepMainRule(Main.class)
        .compile()
        .apply(this::populateBootClasspath)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT)
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) throws Exception {
    assertThat(inspector.clazz(ExtensionApiLibraryClass.class), isAbsent());
    verifyThat(
            inspector, parameters, ExtensionApiLibraryClass.class.getDeclaredMethod("extensionApi"))
        .isOutlinedFromIf(
            Main.class.getDeclaredMethod("main", String[].class), parameters.isDexRuntime());
    verifyThat(
            inspector,
            parameters,
            AnotherExtensionApiLibraryClass.class.getDeclaredMethod("extensionApi"))
        .isOutlinedFromIf(
            Main.class.getDeclaredMethod("main", String[].class), parameters.isDexRuntime());
  }

  public static class Main {
    public static void main(String[] args) {
      ExtensionApiLibraryClass.extensionApi();
      AnotherExtensionApiLibraryClass.extensionApi();
    }
  }
}
