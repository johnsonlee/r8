// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.assumenosideeffects;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentIf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.io.PrintStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AssumeNoSideEffectsOnMethodWithArgumentReturnTest extends TestBase {

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
        .addKeepClassAndMembersRules(Main.class)
        .addKeepRules(
            "-assumenosideeffects class " + Preconditions.class.getTypeName() + " {",
            "  static java.lang.Object checkNotNull(java.lang.Object);",
            "}")
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject preconditionsClass = inspector.clazz(Preconditions.class);
              assertThat(preconditionsClass, isPresentIf(parameters.isCfRuntime()));

              MethodSubject checkNotNullMethod =
                  preconditionsClass.uniqueMethodWithOriginalName("checkNotNull");
              assertThat(checkNotNullMethod, isPresentIf(parameters.isCfRuntime()));

              MethodSubject testMethodSubject =
                  inspector.clazz(Main.class).uniqueMethodWithOriginalName("test");
              assertThat(testMethodSubject, isPresent());
              if (parameters.isCfRuntime()) {
                // Calls are not eliminated due to not running the move-result optimization when
                // compiling to CF.
                assertThat(testMethodSubject, invokesMethod(checkNotNullMethod));
              } else {
                assertTrue(
                    testMethodSubject
                        .streamInstructions()
                        .filter(InstructionSubject::isInvokeMethod)
                        .map(InstructionSubject::getMethod)
                        .allMatch(
                            method ->
                                method.toSourceString().contains(PrintStream.class.getTypeName())));
              }
            })
        .run(parameters.getRuntime(), Main.class, "Hello", ",", " ", "world", "!")
        .assertSuccessWithOutputLines("Hello, world!");
  }

  static class Main {

    public static void main(String[] args) {
      test(args[0], args[1], args[2], args[3], args[4]);
    }

    static void test(Object p1, Object p2, Object p3, Object p4, Object p5) {
      Object nonNullP1 = Preconditions.checkNotNull(p1);
      System.out.print(nonNullP1);
      Object nonNullP2 = Preconditions.checkNotNull(p2);
      System.out.print(nonNullP2);
      Object nonNullP3 = Preconditions.checkNotNull(p3);
      System.out.print(nonNullP3);
      Object nonNullP4 = Preconditions.checkNotNull(p4);
      System.out.print(nonNullP4);
      Object nonNullP5 = Preconditions.checkNotNull(p5);
      System.out.println(nonNullP5);
    }
  }

  static class Preconditions {

    static <T> T checkNotNull(T t) {
      if (t == null) {
        throw new RuntimeException("Expected non-null object");
      }
      return t;
    }
  }
}
