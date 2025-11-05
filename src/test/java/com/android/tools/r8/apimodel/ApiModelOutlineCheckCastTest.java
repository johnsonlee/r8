// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForMethod;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.verifyThat;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.testing.AndroidBuildVersion;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.lang.reflect.Method;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelOutlineCheckCastTest extends TestBase {

  private static final AndroidApiLevel classApiLevel = AndroidApiLevel.M;

  private static final String[] EXPECTED = new String[] {"LibraryClass::foo"};

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  private void setupTestBuilder(TestCompilerBuilder<?, ?, ?, ?, ?> testBuilder) throws Exception {
    setupTestBuilderWithoutProgram(
        testBuilder.addProgramClasses(Main.class).addAndroidBuildVersion(getApiLevelForRuntime()));
  }

  private void setupTestBuilderWithoutProgram(TestCompilerBuilder<?, ?, ?, ?, ?> testBuilder)
      throws Exception {
    testBuilder
        .markAndroidBuildVersionAsActive(getApiLevelForRuntime())
        .addLibraryClasses(LibraryClass.class, LibraryProvider.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters)
        .apply(setMockApiLevelForClass(LibraryProvider.class, AndroidApiLevel.B))
        .apply(
            setMockApiLevelForMethod(
                LibraryProvider.class.getDeclaredMethod("getObject", boolean.class),
                AndroidApiLevel.B))
        .apply(setMockApiLevelForClass(LibraryClass.class, classApiLevel))
        .apply(setMockApiLevelForMethod(LibraryClass.class.getDeclaredMethod("foo"), classApiLevel))
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        .apply(ApiModelingTestHelper::enableOutliningOfMethods)
        .apply(ApiModelingTestHelper::disableGlobalSyntheticCheck);
  }

  public AndroidApiLevel getApiLevelForRuntime() {
    return parameters.isCfRuntime()
        ? AndroidApiLevel.B
        : parameters.getRuntime().asDex().maxSupportedApiLevel();
  }

  public boolean addToBootClasspath() {
    return getApiLevelForRuntime().isGreaterThanOrEqualTo(classApiLevel);
  }

  @Test
  public void testReference() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(Main.class)
        .addAndroidBuildVersion(parameters.getApiLevel())
        .addLibraryClasses(LibraryProvider.class)
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  @Test
  public void testD8Cf() throws Exception {
    parameters.assumeCfRuntime();
    testForD8(parameters.getBackend())
        .setMinApi(parameters)
        .setMode(CompilationMode.DEBUG)
        .apply(this::setupTestBuilder)
        .compile()
        .inspect(
            inspector ->
                // Compiling to CF should never result in stubs and outlining for API modeling.
                verifyThat(inspector, parameters, LibraryClass.class)
                    .hasNotCheckCastOutlinedFrom(getMainMethod()))
        .applyIf(
            addToBootClasspath(),
            b -> b.addBootClasspathClasses(LibraryClass.class, LibraryProvider.class),
            b -> b.addBootClasspathClasses(LibraryProvider.class))
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  @Test
  public void testD8CfAndDexNoDesugaring() throws Exception {
    parameters.assumeDexRuntime();
    D8TestCompileResult compileResult =
        testForD8(Backend.CF)
            .setMinApi(parameters)
            .setMode(CompilationMode.DEBUG)
            .apply(this::setupTestBuilder)
            .compile();
    Path out = compileResult.writeToZip();
    testForD8()
        .disableDesugaring()
        .setMode(CompilationMode.DEBUG)
        .apply(this::setupTestBuilderWithoutProgram)
        .addProgramFiles(out)
        .collectSyntheticItems()
        .compile()
        .apply(
            noDesugarCompileResult ->
                inspect(
                    noDesugarCompileResult.inspector(), noDesugarCompileResult.getSyntheticItems()))
        .applyIf(
            addToBootClasspath(),
            b -> b.addBootClasspathClasses(LibraryClass.class, LibraryProvider.class),
            b -> b.addBootClasspathClasses(LibraryProvider.class))
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  @Test
  public void testD8Debug() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .setMode(CompilationMode.DEBUG)
        .apply(this::setupTestBuilder)
        .collectSyntheticItems()
        .compile()
        .apply(
            compileResult -> inspect(compileResult.inspector(), compileResult.getSyntheticItems()))
        .applyIf(
            addToBootClasspath(),
            b -> b.addBootClasspathClasses(LibraryClass.class, LibraryProvider.class),
            b -> b.addBootClasspathClasses(LibraryProvider.class))
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  @Test
  public void testD8Release() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .setMode(CompilationMode.RELEASE)
        .apply(this::setupTestBuilder)
        .collectSyntheticItems()
        .compile()
        .apply(
            compileResult -> inspect(compileResult.inspector(), compileResult.getSyntheticItems()))
        .applyIf(
            addToBootClasspath(),
            b -> b.addBootClasspathClasses(LibraryClass.class, LibraryProvider.class),
            b -> b.addBootClasspathClasses(LibraryProvider.class))
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .apply(this::setupTestBuilder)
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            options -> options.desugarSpecificOptions().minimizeSyntheticNames = true)
        .collectSyntheticItems()
        .compile()
        .apply(
            compileResult -> inspect(compileResult.inspector(), compileResult.getSyntheticItems()))
        .applyIf(
            addToBootClasspath(),
            b -> b.addBootClasspathClasses(LibraryClass.class, LibraryProvider.class),
            b -> b.addBootClasspathClasses(LibraryProvider.class))
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  private void inspect(CodeInspector inspector, SyntheticItemsTestUtils syntheticItems)
      throws Exception {
    verifyThat(inspector, parameters, LibraryClass.class, syntheticItems)
        .hasCheckCastOutlinedFromUntil(getMainMethod(), classApiLevel);
  }

  private static Method getMainMethod() throws NoSuchMethodException {
    return Main.class.getMethod("main", String[].class);
  }

  private void checkOutput(SingleTestRunResult<?> runResult) {
    if (addToBootClasspath()) {
      runResult.assertSuccessWithOutputLines(EXPECTED);
    } else {
      runResult.assertSuccessWithOutputLines("Not calling foo on object");
    }
  }

  // Only present from api 23.
  public static class LibraryClass {

    public void foo() {
      System.out.println("LibraryClass::foo");
    }
  }

  public static class LibraryProvider {

    public static Object getObject(boolean hasApiLevel) {
      if (hasApiLevel) {
        return new LibraryClass();
      } else {
        return new Object();
      }
    }
  }

  public static class Main {

    public static void main(String[] args) {
      Object object = LibraryProvider.getObject(AndroidBuildVersion.VERSION >= 23);
      if (AndroidBuildVersion.VERSION >= 23) {
        ((LibraryClass) object).foo();
      } else {
        System.out.println("Not calling foo on object");
      }
    }
  }
}
