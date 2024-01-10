// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
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
public class IfMemberRuleWithVerticallyMergedParameterTest extends TestBase {

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
            "  public static void print("
                + A.class.getTypeName()
                + ", "
                + B.class.getTypeName()
                + ");",
            "}",
            "-keep class " + KeptByIf.class.getTypeName())
        .addVerticallyMergedClassesInspector(
            inspector -> inspector.assertMergedIntoSubtype(A.class))
        .setMinApi(parameters)
        .compile()
        .inspect(inspector -> assertThat(inspector.clazz(KeptByIf.class), isPresent()))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("B", "B");
  }

  static class Main {

    public static void main(String[] args) {
      print(new B(), new B());
    }

    public static void print(A a, B b) {
      System.out.println(a);
      System.out.println(b);
    }
  }

  static class A {}

  static class B extends A {

    @Override
    public String toString() {
      return "B";
    }
  }

  static class KeptByIf {}
}
