// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.verifyThat;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.apimodel.extension.ExtensionApiLibraryClass;
import com.android.tools.r8.apimodel.extension.subpackage.SubpackageExtensionApiLibraryClass;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Path;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelOutlineMethodExtensionApiSubpackageTest extends TestBase {

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines(
          "ExtensionApiLibraryClass::extensionApi",
          "SubpackageExtensionApiLibraryClass::extensionApi");

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean packageIsExtension;

  @Parameter(2)
  public boolean subpackageIsExtension;

  @Parameter(3)
  public boolean useExtensionJar;

  @Parameters(
      name = "{0}, packageIsExtension = {1}, subpackageIsExtension = {2}, useExtensionJar = {3}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        BooleanUtils.values(),
        BooleanUtils.values(),
        BooleanUtils.values());
  }

  private static Path extensionLibraryJar;
  private static Path subPackagExtensionLibraryJar;

  @BeforeClass
  public static void setUp() throws Exception {
    extensionLibraryJar =
        zipWithTestClasses(
            getStaticTemp().newFolder().toPath().resolve("extension.jar"),
            ExtensionApiLibraryClass.class);
    subPackagExtensionLibraryJar =
        zipWithTestClasses(
            getStaticTemp().newFolder().toPath().resolve("sub_package_extension.jar"),
            SubpackageExtensionApiLibraryClass.class);
  }

  private void setupTestBuilder(TestCompilerBuilder<?, ?, ?, ?, ?> testBuilder) throws Exception {
    testBuilder
        .addLibraryFiles(extensionLibraryJar, subPackagExtensionLibraryJar)
        .addDefaultRuntimeLibrary(parameters)
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .addOptionsModification(
            options -> {
              if (useExtensionJar) {
                StringBuilder androidApiExtensionLibraries = new StringBuilder();
                if (packageIsExtension) {
                  androidApiExtensionLibraries.append(extensionLibraryJar);
                }
                if (subpackageIsExtension) {
                  if (packageIsExtension) {
                    androidApiExtensionLibraries.append(",");
                  }
                  androidApiExtensionLibraries.append(subPackagExtensionLibraryJar);
                }
                if (packageIsExtension || subpackageIsExtension) {
                  options.apiModelingOptions().androidApiExtensionLibraries =
                      androidApiExtensionLibraries.toString();
                } else {
                  assertTrue(androidApiExtensionLibraries.toString().isEmpty());
                }
              } else {
                StringBuilder androidApiExtensionPackages = new StringBuilder();
                if (packageIsExtension) {
                  String typeName = ExtensionApiLibraryClass.class.getTypeName();
                  androidApiExtensionPackages.append(typeName, 0, typeName.lastIndexOf('.'));
                }
                if (subpackageIsExtension) {
                  String typeName = SubpackageExtensionApiLibraryClass.class.getTypeName();
                  if (packageIsExtension) {
                    androidApiExtensionPackages.append(",");
                  }
                  androidApiExtensionPackages.append(typeName, 0, typeName.lastIndexOf('.'));
                }
                options.apiModelingOptions().androidApiExtensionPackages =
                    androidApiExtensionPackages.toString();
              }
            })
        // TODO(b/213552119): Remove when enabled by default.
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        .apply(ApiModelingTestHelper::enableOutliningOfMethods)
        .apply(ApiModelingTestHelper::disableStubbingOfClasses);
  }

  private void populateBootClasspath(TestCompileResult<?, ?> compilerResult) throws Exception {
    compilerResult.addBootClasspathClasses(
        ExtensionApiLibraryClass.class, SubpackageExtensionApiLibraryClass.class);
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
            Main.class.getDeclaredMethod("main", String[].class),
            packageIsExtension && parameters.isDexRuntime());
    verifyThat(
            inspector,
            parameters,
            SubpackageExtensionApiLibraryClass.class.getDeclaredMethod("extensionApi"))
        .isOutlinedFromIf(
            Main.class.getDeclaredMethod("main", String[].class),
            subpackageIsExtension && parameters.isDexRuntime());
  }

  public static class Main {
    public static void main(String[] args) {
      ExtensionApiLibraryClass.extensionApi();
      SubpackageExtensionApiLibraryClass.extensionApi();
    }
  }
}
