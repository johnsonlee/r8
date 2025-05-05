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
public class PartialCompilationReflectiveIdentificationTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    parameters.assumeCanUseR8Partial();
    testForR8Partial(parameters)
        .addR8IncludedClasses(IncludedClass.class)
        .addR8ExcludedClasses(ExcludedMain.class)
        .compile()
        .run(parameters.getRuntime(), ExcludedMain.class)
        .assertSuccessWithEmptyOutput();
  }

  static class ExcludedMain {

    public static void main(String[] args) throws ClassNotFoundException {
      Class.forName(
          "com.android.tools.r8.partial.PartialCompilationReflectiveIdentificationTest"
              + "$IncludedClass");
    }
  }

  static class IncludedClass {}
}
