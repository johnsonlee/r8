// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ConditionalKeepOnUsedMethodTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("0");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withMinimumApiLevel().build();
  }

  public ConditionalKeepOnUsedMethodTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(ConditionalKeepOnUsedMethodTest.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .addKeepRules("-if class **$A { *** used(); } -keepclassmembers class <1>$A { <2> x; }")
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(
            inspector ->
                assertThat(
                    inspector.clazz(A.class).uniqueFieldWithOriginalName("x"),
                    // TODO(b/322910135): The conditional rule should have kept x.
                    isAbsent()));
  }

  static class A {
    // This method is used but its value trivial.
    public static int used() {
      return 0;
    }

    public static final int x = 1;
  }

  static class TestClass {

    public static void main(String[] args) {
      A a = System.nanoTime() < 0 ? null : new A();
      System.out.println(a.used());
    }
  }
}
