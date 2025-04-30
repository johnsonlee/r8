// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PartialCompilationLambdaImplementsIncludedInterfaceTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    parameters.assumeCanUseR8Partial();
    testForR8Partial(parameters.getBackend())
        .addR8IncludedClasses(IncludedInterface.class)
        .addR8ExcludedClasses(ExcludedMain.class)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), ExcludedMain.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  static class ExcludedMain {

    public static void main(String[] args) {
      IncludedInterface i = () -> System.out.println("Hello, world!");
      i.method();
    }
  }

  interface IncludedInterface {

    void method();
  }
}
