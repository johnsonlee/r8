// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.ifrule;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IfMemberRuleWithUnusedParameterTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepRules(
            "-if class " + Main.class.getTypeName() + " {",
            "  public static void print(" + Object.class.getTypeName() + ");",
            "}",
            "-keep class " + KeptByIf.class.getTypeName())
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              // Verify unused parameter is removed.
              MethodSubject printMethodSubject =
                  inspector.clazz(Main.class).uniqueMethodWithOriginalName("print");
              assertThat(printMethodSubject, isPresent());
              assertTrue(printMethodSubject.getParameters().isEmpty());

              // Verify -if rule is applied.
              assertThat(inspector.clazz(KeptByIf.class), isPresent());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  static class Main {

    public static void main(String[] args) {
      print(args);
    }

    public static void print(Object unused) {
      System.out.println("Hello, world!");
    }
  }

  static class KeptByIf {}
}
