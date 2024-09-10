// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk11.desugar.nest;

import static com.android.tools.r8.jdk11.desugar.nest.BasicNestHostWithInnerClassConstructors.getExpectedResult;

import com.android.tools.r8.Jdk9TestUtils;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NestConstructorRemovedArgTest extends TestBase {

  private static final Class<?> MAIN_CLASS = BasicNestHostWithInnerClassConstructors.class;

  public NestConstructorRemovedArgTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK11)
        .withDexRuntimes()
        .withApiLevelsStartingAtIncluding(apiLevelWithInvokeCustomSupport())
        .enableApiLevelsForCf()
        .build();
  }

  @Test
  public void testRemoveArgConstructorNestsR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addKeepMainRule(MAIN_CLASS)
        .addDontObfuscate()
        .setMinApi(parameters)
        .addOptionsModification(options -> options.enableClassInlining = false)
        .addProgramClassesAndInnerClasses(MAIN_CLASS)
        .applyIf(parameters.isCfRuntime(), Jdk9TestUtils.addJdk9LibraryFiles(temp))
        .compile()
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutputLines(getExpectedResult());
  }

  @Test
  public void testRemoveArgConstructorNestsR8NoTreeShaking() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addDontShrink()
        .addKeepMainRule(MAIN_CLASS)
        .addDontObfuscate()
        .setMinApi(parameters)
        .addOptionsModification(options -> options.enableClassInlining = false)
        .addProgramClassesAndInnerClasses(MAIN_CLASS)
        .applyIf(parameters.isCfRuntime(), Jdk9TestUtils.addJdk9LibraryFiles(temp))
        .compile()
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutputLines(getExpectedResult());
  }
}
