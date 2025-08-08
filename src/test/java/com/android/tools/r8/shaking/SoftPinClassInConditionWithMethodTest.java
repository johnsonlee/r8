// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SoftPinClassInConditionWithMethodTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static final String EXPECTED_OUTPUT = StringUtils.lines("2");

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .addKeepRules(
            "-if class "
                + A.class.getTypeName()
                + " { void foo(java.lang.Class); }"
                + " -keepclasseswithmembers class "
                + B.class.getTypeName()
                + " { <init>(...); }")
        .run(parameters.getRuntime(), TestClass.class)
        .inspect(
            inspector -> {
              // Class A is still present with no members and no reference to it at all. It could
              // have been removed.
              assertThat(inspector.clazz(A.class), isPresent());
              assertTrue(inspector.clazz(A.class).allMethods().isEmpty());
              assertEquals(
                  0,
                  inspector
                      .clazz(TestClass.class)
                      .mainMethod()
                      .streamInstructions()
                      .filter(InstructionSubject::isNewInstance)
                      .count());
            })
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  static class A {
    void foo(Class<?> clazz) {
      System.out.println(clazz.getDeclaredConstructors().length);
    }
  }

  static class B {
    B() {}

    B(int i) {}
  }

  static class TestClass {

    public static void main(String[] args) {
      new A().foo(System.nanoTime() > 0 ? B.class : null);
    }
  }
}
