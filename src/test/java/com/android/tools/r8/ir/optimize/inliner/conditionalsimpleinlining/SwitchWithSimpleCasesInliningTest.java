// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.inliner.conditionalsimpleinlining;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SwitchWithSimpleCasesInliningTest extends TestBase {

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

              MethodSubject mainMethodSubject = mainClassSubject.mainMethod();
              assertThat(mainMethodSubject, isPresent());
              // TODO(b/331337747): Account for constant canonicalization in constraint analysis.
              assertEquals(
                  parameters.isCfRuntime(),
                  mainMethodSubject
                      .streamInstructions()
                      .filter(InstructionSubject::isConstString)
                      .allMatch(i -> i.isConstString("O")));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            "true", "false", "true", "true", "false", "true", "false", "false", "false", "false",
            "true", "true", "false", "true", "false");
  }

  static class Main {

    public static void main(String[] args) {
      // Known.
      System.out.println(isEnabled("A"));
      System.out.println(isEnabled("B"));
      System.out.println(isEnabled("C"));
      System.out.println(isEnabled("D"));
      System.out.println(isEnabled("E"));
      System.out.println(isEnabled("F"));
      System.out.println(isEnabled("G"));
      System.out.println(isEnabled("H"));
      System.out.println(isEnabled("I"));
      System.out.println(isEnabled("J"));
      System.out.println(isEnabled("K"));
      System.out.println(isEnabled("L"));
      System.out.println(isEnabled("M"));
      System.out.println(isEnabled("N"));
      // Unknown.
      System.out.println(isEnabled("O"));
    }

    public static boolean isEnabled(String feature) {
      switch (feature) {
        case "A":
          return true;
        case "B":
          return false;
        case "C":
          return true;
        case "D":
          return true;
        case "E":
          return false;
        case "F":
          return true;
        case "G":
          return false;
        case "H":
          return false;
        case "I":
          return false;
        case "J":
          return false;
        case "K":
          return true;
        case "L":
          return true;
        case "M":
          return false;
        case "N":
          return true;
        default:
          return hasProperty(feature);
      }
    }

    @NeverInline
    public static boolean hasProperty(String property) {
      return System.getProperty(property) != null;
    }
  }
}
