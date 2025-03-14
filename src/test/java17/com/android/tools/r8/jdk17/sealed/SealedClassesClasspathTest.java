// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk17.sealed;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SealedClassesClasspathTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK17)
        .withDexRuntimes()
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  private void runTest(TestCompilerBuilder<?, ?, ?, ?, ?> builder) throws Exception {
    builder
        .addClasspathClasses(Helper.getSealedClasses())
        .addInnerClassesAndStrippedOuter(getClass())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestRunner.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    runTest(testForD8(parameters.getBackend()));
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    runTest(testForR8(parameters.getBackend()).addKeepMainRule(TestRunner.class));
  }

  public static class TestRunner {

    public static void main(String[] args) {
      System.out.println("Hello, world!");
    }
  }
}
