// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static com.android.tools.r8.naming.retrace.StackTrace.isSameExceptForFileNameAndLineNumber;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.Objects;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RetraceObjectsRequireNotNullTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection parameters() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  @Parameter(0)
  public TestParameters parameters;

  private boolean isCfVmWithModulePrefix() {
    return parameters.isCfRuntime() && !parameters.isCfRuntime(CfVm.JDK8);
  }

  private boolean isApiWithRequireNonNullSupport() {
    return parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.K);
  }

  @Test
  public void testReference() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkRunResult)
        .inspectStackTrace(
            stackTrace -> checkExpectedStackTrace(stackTrace, true, isCfVmWithModulePrefix()));
  }

  @Test
  public void testD8() throws Exception {
    boolean includeObjectsFrame = isApiWithRequireNonNullSupport();
    boolean includeJvmModule = isApiWithRequireNonNullSupport() && isCfVmWithModulePrefix();
    testForD8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkRunResult)
        .inspectStackTrace(
            stackTrace ->
                checkExpectedStackTrace(stackTrace, includeObjectsFrame, includeJvmModule));
  }

  @Test
  public void testD8DebugRetrace() throws Exception {
    boolean includeObjectsFrame = isApiWithRequireNonNullSupport();
    boolean includeJvmModule = isApiWithRequireNonNullSupport() && isCfVmWithModulePrefix();
    testForD8(parameters.getBackend())
        .debug()
        .internalEnableMappingOutput()
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkRunResult)
        .inspectStackTrace(
            stackTrace ->
                checkExpectedStackTrace(
                    stackTrace,
                    // TODO(b/326196974): This should always retrace to include the objects frame.
                    includeObjectsFrame,
                    includeJvmModule));
  }

  @Test
  public void testD8ReleaseRetrace() throws Exception {
    boolean includeObjectsFrame = isApiWithRequireNonNullSupport();
    boolean includeJvmModule = isCfVmWithModulePrefix();
    testForD8(parameters.getBackend())
        .release()
        .internalEnableMappingOutput()
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkRunResult)
        .inspectStackTrace(
            stackTrace ->
                checkExpectedStackTrace(
                    stackTrace,
                    // TODO(b/326196974): This should always retrace to include the objects frame.
                    includeObjectsFrame,
                    includeJvmModule));
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepAttributeSourceFile()
        .addKeepAttributeLineNumberTable()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkRunResult)
        .inspectStackTrace(
            stackTrace ->
                checkExpectedStackTrace(
                    stackTrace,
                    // TODO(b/326196974): This should always retrace to include the objects frame.
                    parameters.isCfRuntime() || isApiWithRequireNonNullSupport(),
                    isCfVmWithModulePrefix()));
  }

  private void checkRunResult(SingleTestRunResult<?> runResult) {
    runResult.assertFailureWithErrorThatThrows(NullPointerException.class);
  }

  private void checkExpectedStackTrace(
      StackTrace stackTrace, boolean includeObjectsFrame, boolean includeJvmModule) {
    StackTrace.Builder builder = StackTrace.builder();
    if (includeObjectsFrame) {
      ClassReference objects = Reference.classFromClass(Objects.class);
      String objectsFrameFormat = (includeJvmModule ? "java.base/" : "") + objects.getTypeName();
      builder.addWithoutFileNameAndLineNumber(objectsFrameFormat, "requireNonNull");
    }
    builder
        .addWithoutFileNameAndLineNumber(A.class, "run")
        .addWithoutFileNameAndLineNumber(Main.class, "main");
    assertThat(stackTrace, isSameExceptForFileNameAndLineNumber(builder.build()));
  }

  public static class A {
    public void run(Object o) {
      Objects.requireNonNull(o);
    }
  }

  public static class Main {

    public static void main(String[] args) {
      (System.nanoTime() > 0 ? new A() : null).run(System.nanoTime() > 0 ? null : new A());
    }
  }
}
