// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk17.nest;

import com.android.tools.r8.JdkClassFileProvider;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NestLambdaJava17Test extends TestBase {

  private static final Class<?> NEST_LAMBDA_CLASS = NestLambda.class;
  private static final String EXPECTED_RESULT =
      StringUtils.lines("printed: inner", "printed from itf: here");

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

  @Test
  public void testReference() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClassesAndInnerClasses(NEST_LAMBDA_CLASS)
        .run(parameters.getRuntime(), NEST_LAMBDA_CLASS)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testJavaD8() throws Exception {
    testForD8(parameters.getBackend())
        .addProgramClassesAndInnerClasses(NEST_LAMBDA_CLASS)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), NEST_LAMBDA_CLASS)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addProgramClassesAndInnerClasses(NestLambda.class)
        .applyIf(
            parameters.isCfRuntime(),
            b -> b.addLibraryProvider(JdkClassFileProvider.fromSystemJdk()))
        .setMinApi(parameters)
        .addKeepMainRule(NEST_LAMBDA_CLASS)
        .run(parameters.getRuntime(), NEST_LAMBDA_CLASS)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }
}
