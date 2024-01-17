// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.rewrite.arrays;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.function.Function;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class UnusedNewArrayOfApiDependentLibraryTypeTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeCfRuntime();
    testForJvm(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithEmptyOutput();
  }

  @Test
  public void testD8Release() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addInnerClasses(getClass())
        .release()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            hasFunctionAsRunTime(),
            TestRunResult::assertSuccessWithEmptyOutput,
            runResult -> runResult.assertFailureWithErrorThatThrows(NoClassDefFoundError.class));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .applyIf(
            !hasFunctionAtCompileTime(), testBuilder -> testBuilder.addDontWarn(Function.class))
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            hasFunctionAsRunTime(),
            TestRunResult::assertSuccessWithEmptyOutput,
            runResult -> runResult.assertFailureWithErrorThatThrows(NoClassDefFoundError.class));
  }

  private void inspect(CodeInspector inspector) {
    MethodSubject mainMethodSubject = inspector.clazz(Main.class).mainMethod();
    assertThat(mainMethodSubject, isPresent());
    assertEquals(canOptimize(), mainMethodSubject.getMethod().getCode().isEmptyVoidMethod());
  }

  private boolean canOptimize() {
    return hasFunctionAtCompileTime() && parameters.isDexRuntime();
  }

  private boolean hasFunctionAtCompileTime() {
    return parameters.isCfRuntime()
        || parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.N);
  }

  private boolean hasFunctionAsRunTime() {
    return parameters.isCfRuntime()
        || parameters.getDexRuntimeVersion().isNewerThanOrEqual(Version.V7_0_0);
  }

  public static class Main {

    public static void main(String[] args) {
      Function[] functions = new Function[0];
    }
  }
}
