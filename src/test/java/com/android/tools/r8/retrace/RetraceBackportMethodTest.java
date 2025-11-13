// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static com.android.tools.r8.naming.retrace.StackTrace.isSame;
import static com.android.tools.r8.naming.retrace.StackTrace.isSameExceptForLineNumbers;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.naming.retrace.StackTrace.StackTraceLine;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RetraceBackportMethodTest extends TestBase {

  static final Class<?> CLASS = RetraceBackportMethodClass.class;
  static final Class<?> CLASS_A = RetraceBackportMethodClass.A.class;
  static final Class<?> CLASS_MAIN = RetraceBackportMethodClass.Main.class;

  static List<Class<?>> getInputClasses() {
    return ImmutableList.of(CLASS, CLASS_A, CLASS_MAIN);
  }

  @Parameters(name = "{0}")
  public static TestParametersCollection parameters() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  @Parameter(0)
  public TestParameters parameters;

  private boolean isCfVmWithModulePrefix() {
    return parameters.isCfRuntime() && !parameters.isCfRuntime(CfVm.JDK8);
  }

  private boolean isApiWithFloorDivIntSupport() {
    return parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.N);
  }

  @Test
  public void testReference() throws Exception {
    parameters.assumeJvmTestParameters();
    boolean includeMathFrame = true;
    boolean includeExternalSyntheticFrame = false;
    boolean includeJvmModule = isCfVmWithModulePrefix();
    boolean doNotCheckLines = true;
    testForJvm(parameters)
        .addProgramClasses(getInputClasses())
        .run(parameters.getRuntime(), CLASS_MAIN)
        .apply(this::checkRunResult)
        .inspectStackTrace(
            stackTrace ->
                checkExpectedStackTrace(
                    stackTrace,
                    includeMathFrame,
                    includeExternalSyntheticFrame,
                    includeJvmModule,
                    doNotCheckLines,
                    null));
  }

  @Test
  public void testD8() throws Exception {
    boolean includeMathFrame = isApiWithFloorDivIntSupport();
    boolean includeExternalSyntheticFrame = !includeMathFrame;
    boolean includeJvmModule = includeMathFrame && isCfVmWithModulePrefix();
    boolean doNotCheckLines = includeMathFrame;
    testForD8(parameters.getBackend())
        .addProgramClasses(getInputClasses())
        .collectSyntheticItems()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), CLASS_MAIN)
        .apply(this::checkRunResult)
        .apply(
            rr ->
                rr.inspectStackTrace(
                    stackTrace ->
                        checkExpectedStackTrace(
                            stackTrace,
                            includeMathFrame,
                            includeExternalSyntheticFrame,
                            includeJvmModule,
                            doNotCheckLines,
                            rr.getSyntheticItems())));
  }

  @Test
  public void testD8DebugRetrace() throws Exception {
    boolean includeMathFrame = true;
    boolean includeExternalSyntheticFrame = false;
    boolean includeJvmModule = isApiWithFloorDivIntSupport() && isCfVmWithModulePrefix();
    boolean doNotCheckLines = isApiWithFloorDivIntSupport();
    testForD8(parameters.getBackend())
        .debug()
        .internalEnableMappingOutput()
        .addProgramClasses(getInputClasses())
        .collectSyntheticItems()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), CLASS_MAIN)
        .apply(this::checkRunResult)
        .apply(
            rr ->
                rr.inspectStackTrace(
                    stackTrace ->
                        checkExpectedStackTrace(
                            stackTrace,
                            includeMathFrame,
                            includeExternalSyntheticFrame,
                            includeJvmModule,
                            doNotCheckLines,
                            rr.getSyntheticItems())));
  }

  @Test
  public void testD8ReleaseRetrace() throws Exception {
    boolean includeMathFrame = true;
    boolean includeExternalSyntheticFrame = false;
    boolean includeJvmModule = isApiWithFloorDivIntSupport() && isCfVmWithModulePrefix();
    boolean doNotCheckLines = isApiWithFloorDivIntSupport();
    testForD8(parameters.getBackend())
        .release()
        .internalEnableMappingOutput()
        .addProgramClasses(getInputClasses())
        .collectSyntheticItems()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), CLASS_MAIN)
        .apply(this::checkRunResult)
        .apply(
            rr ->
                rr.inspectStackTrace(
                    stackTrace ->
                        checkExpectedStackTrace(
                            stackTrace,
                            includeMathFrame,
                            includeExternalSyntheticFrame,
                            includeJvmModule,
                            doNotCheckLines,
                            rr.getSyntheticItems())));
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    boolean includeMathFrame = true;
    boolean includeExternalSyntheticFrame = false;
    boolean includeJvmModule = isCfVmWithModulePrefix();
    boolean doNotCheckLines = parameters.isCfRuntime() || isApiWithFloorDivIntSupport();
    testForR8(parameters.getBackend())
        .addProgramClasses(getInputClasses())
        .addKeepMainRule(CLASS_MAIN)
        .addKeepAttributeSourceFile()
        .addKeepAttributeLineNumberTable()
        .collectSyntheticItems()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), CLASS_MAIN)
        .apply(this::checkRunResult)
        .apply(
            rr ->
                rr.inspectStackTrace(
                    stackTrace ->
                        checkExpectedStackTrace(
                            stackTrace,
                            includeMathFrame,
                            includeExternalSyntheticFrame,
                            includeJvmModule,
                            doNotCheckLines,
                            rr.getSyntheticItems())));
  }

  private void checkRunResult(SingleTestRunResult<?> runResult) {
    runResult.assertFailureWithErrorThatThrows(ArithmeticException.class);
  }

  private void checkExpectedStackTrace(
      StackTrace stackTrace,
      boolean includeMathFrame,
      boolean includeExternalSyntheticFrame,
      boolean includeJvmModule,
      boolean doNotCheckLines,
      SyntheticItemsTestUtils syntheticItems) {
    StackTrace.Builder builder = StackTrace.builder();
    if (includeMathFrame) {
      assertFalse(includeExternalSyntheticFrame);
      ClassReference objects = Reference.classFromClass(Math.class);
      String mathFrameFormat = (includeJvmModule ? "java.base/" : "") + objects.getTypeName();
      builder.add(
          StackTraceLine.builder()
              .setFileName("Math.java")
              .setClassName(mathFrameFormat)
              .setMethodName("floorDiv")
              .setLineNumber(-1)
              .build());
    }
    if (includeExternalSyntheticFrame) {
      assertFalse(includeMathFrame);
      builder.add(
          StackTraceLine.builder()
              .setFileName(SyntheticItemsTestUtils.syntheticFileNameD8())
              .setClassName(syntheticItems.syntheticBackportClass(CLASS_A, 0).getTypeName())
              .setMethodName(SyntheticItemsTestUtils.syntheticMethodName())
              .setLineNumber(parameters.isCfRuntime() ? -1 : 0)
              .build());
    }
    String fileName = ToolHelper.getSourceFileForTestClass(CLASS).getFileName().toString();
    builder
        .add(
            StackTraceLine.builder()
                .setFileName(fileName)
                .setClassName(typeName(CLASS_A))
                .setMethodName("run")
                .setLineNumber(12)
                .build())
        .add(
            StackTraceLine.builder()
                .setFileName(fileName)
                .setClassName(typeName(CLASS_MAIN))
                .setMethodName("main")
                .setLineNumber(19)
                .build());
    if (doNotCheckLines) {
      // We can't check the line numbers when using the native support of Objects.requireNonNull.
      assertThat(stackTrace, isSameExceptForLineNumbers(builder.build()));
    } else {
      assertThat(stackTrace, isSame(builder.build()));
    }
  }
}
