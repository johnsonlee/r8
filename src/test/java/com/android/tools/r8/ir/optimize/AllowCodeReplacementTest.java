// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsentIf;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AllowCodeReplacementTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withMinimumApiLevel().build();
  }

  @Test
  public void testFeatureDisabledByDefault() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepRules(
            "-keepclassmembers,allowshrinking class " + Main.class.getTypeName() + " {",
            "  boolean alwaysFalse();",
            "}")
        .addOptionsModification(options -> assertTrue(options.testing.allowCodeReplacement))
        .setMinApi(parameters)
        .compile()
        .inspect(inspector -> inspect(inspector, false))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  @Test
  public void testAnalyzeKeptMethod() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepRules(
            "-keepclassmembers,allowshrinking class " + Main.class.getTypeName() + " {",
            "  boolean alwaysFalse();",
            "}")
        .addOptionsModification(options -> options.testing.allowCodeReplacement = false)
        .setMinApi(parameters)
        .compile()
        .inspect(inspector -> inspect(inspector, true))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  @Test
  public void testKeepForCodeReplacement() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepRules(
            "-keepclassmembers,allowcodereplacement,allowshrinking class "
                + Main.class.getTypeName()
                + " {",
            "  boolean alwaysFalse();",
            "}")
        .addOptionsModification(options -> options.testing.allowCodeReplacement = false)
        .enableProguardTestOptions()
        .setMinApi(parameters)
        .compile()
        .inspect(inspector -> inspect(inspector, false))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  private void inspect(CodeInspector inspector, boolean isOptimized) {
    ClassSubject mainClassSubject = inspector.clazz(Main.class);
    assertThat(mainClassSubject, isPresent());

    MethodSubject mainMethodSubject = mainClassSubject.mainMethod();
    assertThat(mainMethodSubject, isPresent());
    assertEquals(
        isOptimized,
        inspector
            .clazz(Main.class)
            .mainMethod()
            .streamInstructions()
            .noneMatch(i -> i.isConstString("Unreachable")));

    MethodSubject alwaysFalseMethodSubject =
        mainClassSubject.uniqueMethodWithOriginalName("alwaysFalse");
    assertThat(alwaysFalseMethodSubject, isAbsentIf(isOptimized));
  }

  static class Main {

    public static void main(String[] args) {
      if (alwaysFalse()) {
        System.out.println("Unreachable");
      } else {
        System.out.println("Hello, world!");
      }
    }

    static boolean alwaysFalse() {
      return false;
    }
  }
}
