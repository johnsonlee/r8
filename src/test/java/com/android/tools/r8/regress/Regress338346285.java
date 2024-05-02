// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Regress338346285 extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllApiLevels().withAllRuntimes().build();
  }

  public Regress338346285(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepRules("-keep,allowoptimization class " + TestClass.class.getTypeName() + " { *; }")
        .setMinApi(parameters)
        .compile()
        .inspect(
            codeInspector -> {
              assertThat(
                  codeInspector
                      .clazz(TestClass.class)
                      .uniqueMethodWithFinalName("shouldNotBeRemoved"),
                  isPresent());
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("foo", "bar", "foobar");
  }

  public static class TestClass {
    public void shouldNotBeRemoved() {
      System.out.println("foo");
      System.out.println("bar");
      System.out.println("foobar");
    }

    public static void main(String[] args) {
      new TestClass().shouldNotBeRemoved();
    }
  }
}
