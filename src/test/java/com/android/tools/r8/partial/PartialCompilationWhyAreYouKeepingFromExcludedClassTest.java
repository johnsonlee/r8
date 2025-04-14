// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import static org.hamcrest.CoreMatchers.equalTo;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PartialCompilationWhyAreYouKeepingFromExcludedClassTest extends TestBase {

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
        .addR8ExcludedClasses(Main.class)
        .addR8IncludedClasses(Greeter.class)
        .addKeepRules("-whyareyoukeeping class " + Greeter.class.getTypeName())
        .collectStdout()
        .setMinApi(parameters)
        .compile()
        .assertStdoutThatMatches(
            equalTo(
                StringUtils.lines(
                    Greeter.class.getTypeName(),
                    "|- is referenced from excluded class in partial compilation:",
                    "|  "
                        + ToolHelper.getClassFileForTestClass(Main.class)
                        + ":"
                        + MethodReferenceUtils.toSmaliString(
                            MethodReferenceUtils.mainMethod(Main.class)))))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  static class Main {

    public static void main(String[] args) {
      Greeter.greet();
    }
  }

  static class Greeter {

    static void greet() {
      System.out.println("Hello, world!");
    }
  }
}
