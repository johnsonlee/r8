// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FieldStateArgumentPropagationTest extends TestBase {

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
        .addOptionsModification(
            options -> {
              options.enableFieldAssignmentTracker = false;
              options.enableFieldValueAnalysis = false;
            })
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject mainClassSubject = inspector.clazz(Main.class);
              assertThat(mainClassSubject, isPresent());

              MethodSubject printMethodSubject =
                  mainClassSubject.uniqueMethodWithOriginalName("print");
              assertThat(printMethodSubject, isPresent());
              // TODO(b/296030319): Should be 0.
              assertEquals(1, printMethodSubject.getProgramMethod().getArity());

              MethodSubject printlnMethodSubject =
                  mainClassSubject.uniqueMethodWithOriginalName("println");
              assertThat(printlnMethodSubject, isPresent());
              // TODO(b/296030319): Should be 0.
              assertEquals(1, printlnMethodSubject.getProgramMethod().getArity());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  static class Main {

    public static void main(String[] args) {
      print(Greetings.HELLO.f);
      println(Greetings.WORLD.f);
    }

    @NeverInline
    static void print(String message) {
      System.out.print(message);
    }

    @NeverInline
    static void println(String message) {
      System.out.println(message);
    }
  }

  static class Greetings {

    static final Greetings HELLO;
    static final Greetings WORLD;

    static {
      HELLO = new Greetings("Hello");
      WORLD = new Greetings(", world!");
    }

    final String f;

    Greetings(String f) {
      this.f = f;
    }
  }
}
