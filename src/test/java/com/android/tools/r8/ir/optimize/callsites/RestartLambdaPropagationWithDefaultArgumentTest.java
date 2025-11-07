// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.callsites;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsentIf;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
public class RestartLambdaPropagationWithDefaultArgumentTest extends TestBase {

  // Deliberately setting the highest bit in this mask to be able to distinguish it from the int 2.
  private static final int FLAGS = 0b10 | (1 << 31);

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesAndAllApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .collectSyntheticItems()
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspectWithSyntheticItems(
            (inspector, syntheticItems) -> {
              ClassSubject mainClassSubject = inspector.clazz(Main.class);
              assertThat(mainClassSubject, isPresent());

              ClassSubject lambdaClassSubject =
                  inspector.clazz(syntheticItems.syntheticLambdaClass(Main.class, 0));
              assertThat(lambdaClassSubject, isPresent());
              assertEquals(0, lambdaClassSubject.allInstanceFields().size());

              MethodSubject lambdaInitSubject = lambdaClassSubject.uniqueInstanceInitializer();
              assertThat(
                  lambdaInitSubject,
                  isAbsentIf(parameters.canInitNewInstanceUsingSuperclassConstructor()));
              if (lambdaInitSubject.isPresent()) {
                assertEquals(0, lambdaInitSubject.getParameters().size());
              }

              MethodSubject mainMethodSubject = mainClassSubject.mainMethod();
              assertThat(mainMethodSubject, isPresent());
              assertTrue(
                  mainMethodSubject
                      .streamInstructions()
                      .noneMatch(
                          instruction -> instruction.isConstString("DefaultValueNeverUsed")));
              assertTrue(
                  mainMethodSubject
                      .streamInstructions()
                      .noneMatch(
                          instruction ->
                              instruction.isConstString("Unused[DefaultValueAlwaysUsed]")));

              MethodSubject restartableMethodSubject =
                  mainClassSubject.uniqueMethodWithOriginalName("restartableMethod");
              assertThat(restartableMethodSubject, isPresent());
              assertTrue(
                  restartableMethodSubject
                      .streamInstructions()
                      .anyMatch(instruction -> instruction.isConstNumber(FLAGS)));
              assertTrue(
                  restartableMethodSubject
                      .streamInstructions()
                      .noneMatch(
                          instruction ->
                              instruction.isConstString("Unused[DefaultValueNeverUsed]")));
              assertTrue(
                  restartableMethodSubject
                      .streamInstructions()
                      .anyMatch(
                          instruction -> instruction.isConstString("DefaultValueAlwaysUsed")));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            "Postponing!",
            "Restarting!",
            "DefaultValueNeverUsed",
            "DefaultValueAlwaysUsed",
            Integer.toString(FLAGS),
            "Stopping!");
  }

  static class Main {

    public static void main(String[] args) {
      Runnable restarter =
          restartableMethod("DefaultValueNeverUsed", "Unused[DefaultValueAlwaysUsed]", FLAGS, true);
      restarter.run();
    }

    @NeverInline
    static Runnable restartableMethod(
        String defaultValueNeverUsed, String defaultValueAlwaysUsed, int flags, boolean doRestart) {
      if ((flags & 1) != 0) {
        defaultValueNeverUsed = "Unused[DefaultValueNeverUsed]";
      }
      if ((flags & 2) != 0) {
        defaultValueAlwaysUsed = "DefaultValueAlwaysUsed";
      }
      if (doRestart) {
        System.out.println("Postponing!");
        String finalDefaultValueNeverUsed = defaultValueNeverUsed;
        String finalDefaultValueAlwaysUsed = defaultValueAlwaysUsed;
        return () -> {
          System.out.println("Restarting!");
          Runnable restarter =
              restartableMethod(
                  finalDefaultValueNeverUsed, finalDefaultValueAlwaysUsed, flags, false);
          if (restarter == null) {
            System.out.println("Stopping!");
          } else {
            throw new RuntimeException();
          }
        };
      }
      System.out.println(defaultValueNeverUsed);
      System.out.println(defaultValueAlwaysUsed);
      System.out.println(flags);
      return null;
    }
  }
}
