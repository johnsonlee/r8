// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.workaround;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvalidVerifierTypePropagationAfterInstanceOfTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Main.class)
        .apply(runResult -> checkRunResult(runResult, false));
  }

  @Test
  public void testD8Debug() throws Exception {
    testForD8(parameters.getBackend())
        .addInnerClasses(getClass())
        .applyIf(parameters.isDexRuntime(), b -> b.setMinApi(parameters))
        .debug()
        .run(parameters.getRuntime(), Main.class)
        .apply(runResult -> checkRunResult(runResult, false));
  }

  @Test
  public void testD8Release() throws Exception {
    testForD8(parameters.getBackend())
        .addInnerClasses(getClass())
        .applyIf(parameters.isDexRuntime(), b -> b.setMinApi(parameters))
        .release()
        .run(parameters.getRuntime(), Main.class)
        .apply(runResult -> checkRunResult(runResult, false));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepClassAndMembersRules(Main.class, A.class)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .apply(runResult -> checkRunResult(runResult, true));
  }

  private void checkRunResult(TestRunResult<?> runResult, boolean isR8) {
    runResult.applyIf(
        parameters.isCfRuntime()
            || parameters.getDexRuntimeVersion().isOlderThan(Version.V7_0_0)
            || parameters.getDexRuntimeVersion().isNewerThanOrEqual(Version.V15_0_0)
            || isR8,
        TestRunResult::assertSuccessWithEmptyOutput,
        rr -> runResult.assertFailureWithErrorThatThrows(VerifyError.class));
  }

  static class Main {

    public static void main(String[] args) {
      A a = args.length == 0 ? null : new A();
      Object o = a;
      if (o instanceof String) {
        System.out.println(a.f);
      }
    }
  }

  static class A {

    String f;
  }
}
