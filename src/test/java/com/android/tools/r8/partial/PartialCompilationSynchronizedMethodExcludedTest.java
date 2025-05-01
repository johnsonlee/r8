// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PartialCompilationSynchronizedMethodExcludedTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static final String EXPECTED_OUTPUT = StringUtils.lines("Hello, world!");

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  private void checkMonitorEnterAndExit(CodeInspector inspector, long expected) {
    assertEquals(
        expected,
        inspector
            .clazz(ClassWithSynchronizedMethod.class)
            .uniqueMethodWithOriginalName("m")
            .streamInstructions()
            .filter(
                instructionSubject ->
                    instructionSubject.isMonitorEnter() || instructionSubject.isMonitorExit())
            .count());
  }

  private void expectedMonitorEnterAndExit(CodeInspector inspector) {
    checkMonitorEnterAndExit(inspector, 3);
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .compile()
        .inspect(this::expectedMonitorEnterAndExit)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8Partial() throws Exception {
    parameters.assumeCanUseR8Partial();
    testForR8Partial(parameters.getBackend())
        .addR8IncludedClasses(TestClass.class)
        .addR8ExcludedClasses(ClassWithSynchronizedMethod.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .compile()
        .inspect(this::expectedMonitorEnterAndExit)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  static class ClassWithSynchronizedMethod {

    public synchronized void m() {
      notifyAll();
      System.out.println("Hello, world!");
    }
  }

  static class TestClass {
    public static void main(String[] args) {
      new ClassWithSynchronizedMethod().m();
    }
  }
}
