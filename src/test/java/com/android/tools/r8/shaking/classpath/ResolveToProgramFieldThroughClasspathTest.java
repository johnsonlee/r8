// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.classpath;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ResolveToProgramFieldThroughClasspathTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters)
        .addProgramClasses(Main.class, A.class)
        .addClasspathClasses(B.class)
        .addKeepMainRule(Main.class)
        // Keep A since B inherits from them.
        .addKeepClassAndDefaultConstructor(A.class)
        // Keep A.field and A.staticField since B assigns them.
        .addKeepRules(
            "-keep,allowobfuscation,allowshrinking class " + A.class.getTypeName() + " {",
            "  java.lang.String field;",
            "  static java.lang.String staticField;",
            "}")
        .compile()
        .addRunClasspathClasses(B.class)
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(NoSuchFieldError.class);
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println(new B().field);
      System.out.println(B.staticField);
    }
  }

  static class A {

    static String staticField;

    String field;
  }

  // Added on the classpath.
  static class B extends A {

    static {
      staticField = "staticField";
    }

    B() {
      field = "field";
    }
  }
}
