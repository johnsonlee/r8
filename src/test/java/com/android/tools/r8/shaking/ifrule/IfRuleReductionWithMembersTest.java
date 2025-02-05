// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.ifrule;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IfRuleReductionWithMembersTest extends TestBase {

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
            "-if class * {",
            "  static boolean *;",
            "}",
            "-keep class <1> { static void unused(); }")
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector ->
                assertThat(
                    inspector.clazz(Main.class).uniqueMethodWithOriginalName("unused"),
                    isPresent()))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("true", "true");
  }

  static class Main {

    static boolean f = System.currentTimeMillis() > 0;

    static boolean g = System.currentTimeMillis() > 0;

    public static void main(String[] args) {
      System.out.println(f);
      System.out.println(g);
    }

    static void unused() {}
  }
}
