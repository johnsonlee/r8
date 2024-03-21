// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.callsites;

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
public class RestartLambdaPropagationTest extends TestBase {

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
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject mainClassSubject = inspector.clazz(Main.class);
              assertThat(mainClassSubject, isPresent());

              MethodSubject restartableMethodSubject =
                  mainClassSubject.uniqueMethodWithOriginalName("restartableMethod");
              assertThat(restartableMethodSubject, isPresent());
              assertEquals(
                  parameters.isDexRuntime() ? 1 : 2,
                  restartableMethodSubject.getParameters().size());
              assertEquals(
                  parameters.isDexRuntime(),
                  restartableMethodSubject
                      .streamInstructions()
                      .anyMatch(instruction -> instruction.isConstNumber(42)));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Postponing!", "Restarting!", "42", "Stopping!");
  }

  static class Main {

    public static void main(String[] args) {
      Runnable restarter = restartableMethod(true, 42);
      restarter.run();
    }

    @NeverInline
    static Runnable restartableMethod(boolean doRestart, int flags) {
      if (doRestart) {
        System.out.println("Postponing!");
        return () -> {
          System.out.println("Restarting!");
          Runnable restarter = restartableMethod(false, flags);
          if (restarter == null) {
            System.out.println("Stopping!");
          } else {
            throw new RuntimeException();
          }
        };
      }
      System.out.println(flags);
      return null;
    }
  }
}
