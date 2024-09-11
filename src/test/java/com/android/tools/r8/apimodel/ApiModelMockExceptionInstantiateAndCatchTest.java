// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForMethod;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.verifyThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.GlobalSyntheticsTestingConsumer;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelMockExceptionInstantiateAndCatchTest extends TestBase {

  private final AndroidApiLevel mockExceptionLevel = AndroidApiLevel.P;

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withAllRuntimes()
        .withApiLevelsStartingAtIncluding(AndroidApiLevel.L)
        .build();
  }

  private void setupTestCompileBuilder(TestCompilerBuilder<?, ?, ?, ?, ?> testBuilder)
      throws Exception {
    testBuilder
        .addProgramClasses(Main.class)
        .addLibraryClasses(LibraryException.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters)
        .apply(ApiModelingTestHelper::enableStubbingOfClassesAndDisableGlobalSyntheticCheck)
        .apply(setMockApiLevelForClass(LibraryException.class, mockExceptionLevel))
        .apply(
            setMockApiLevelForMethod(
                LibraryException.class.getDeclaredConstructor(String.class), mockExceptionLevel));
  }

  private void setupTestRuntimeBuilder(TestCompilerBuilder<?, ?, ?, ?, ?> testBuilder) {
    testBuilder.setMinApi(parameters).addAndroidBuildVersion();
  }

  private void setupTestBuilder(TestCompilerBuilder<?, ?, ?, ?, ?> testBuilder) throws Exception {
    setupTestCompileBuilder(testBuilder);
    setupTestRuntimeBuilder(testBuilder);
  }

  private boolean exceptionPresentAtRuntime() {
    return parameters.isCfRuntime()
        || parameters
            .getRuntime()
            .maxSupportedApiLevel()
            .isGreaterThanOrEqualTo(mockExceptionLevel);
  }

  @Test
  public void testD8Debug() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .debug()
        .apply(this::setupTestBuilder)
        .compile()
        .applyIf(
            exceptionPresentAtRuntime(), b -> b.addBootClasspathClasses(LibraryException.class))
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  @Test
  public void testD8Release() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .release()
        .apply(this::setupTestBuilder)
        .compile()
        .applyIf(
            exceptionPresentAtRuntime(), b -> b.addBootClasspathClasses(LibraryException.class))
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  @Test
  public void testD8MergeIndexed() throws Exception {
    parameters.assumeDexRuntime();
    testD8Merge(OutputMode.DexIndexed);
  }

  @Test
  public void testD8MergeFilePerClass() throws Exception {
    parameters.assumeDexRuntime();
    testD8Merge(OutputMode.DexFilePerClass);
  }

  @Test
  public void testD8MergeFilePerClassFile() throws Exception {
    parameters.assumeDexRuntime();
    testD8Merge(OutputMode.DexFilePerClassFile);
  }

  public void testD8Merge(OutputMode outputMode) throws Exception {
    GlobalSyntheticsTestingConsumer globals = new GlobalSyntheticsTestingConsumer();
    Path incrementalOut =
        testForD8()
            .debug()
            .setOutputMode(outputMode)
            .setIntermediate(true)
            .apply(b -> b.getBuilder().setGlobalSyntheticsConsumer(globals))
            .apply(this::setupTestCompileBuilder)
            .compile()
            .writeToZip();

    if (parameters.getApiLevel().isLessThan(mockExceptionLevel)) {
      if (outputMode == OutputMode.DexIndexed) {
        assertTrue(globals.hasGlobals());
        assertTrue(globals.isSingleGlobal());
      } else {
        assertTrue(globals.hasGlobals());
        // The Main class reference the mock and should have globals.
        assertNotNull(globals.getProvider(Reference.classFromClass(Main.class)));
      }
    } else {
      assertFalse(globals.hasGlobals());
    }

    testForD8()
        .debug()
        .addProgramFiles(incrementalOut)
        .apply(b -> b.getBuilder().addGlobalSyntheticsResourceProviders(globals.getProviders()))
        .apply(this::setupTestRuntimeBuilder)
        .compile()
        .applyIf(
            exceptionPresentAtRuntime(), b -> b.addBootClasspathClasses(LibraryException.class))
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .apply(this::setupTestBuilder)
        .addKeepMainRule(Main.class)
        .compile()
        .applyIf(
            exceptionPresentAtRuntime(), b -> b.addBootClasspathClasses(LibraryException.class))
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput)
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    verifyThat(inspector, parameters, LibraryException.class).stubbedUntil(mockExceptionLevel);
  }

  private void checkOutput(SingleTestRunResult<?> runResult) {
    if (exceptionPresentAtRuntime()) {
      runResult.assertSuccessWithOutputLines("Valid behaviour");
    } else {
      runResult.assertSuccessWithOutputLines("java.lang.NoClassDefFoundError, false");
    }
  }

  // Only present from api level P.
  public static class LibraryException extends Exception {
    public LibraryException(String message) {
      super(message);
    }
  }

  public static class Main {

    public static void main(String[] args) {
      try {
        throw new LibraryException("Failed");
      } catch (LibraryException e) {
        System.out.println("Valid behaviour");
      } catch (Throwable e) {
        System.out.println(e + ", " + (e instanceof LibraryException));
      }
    }
  }
}
