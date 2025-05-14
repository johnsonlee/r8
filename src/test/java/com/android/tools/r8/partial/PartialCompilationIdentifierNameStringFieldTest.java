// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PartialCompilationIdentifierNameStringFieldTest extends TestBase {

  private static final String identifierNameStringRule =
      StringUtils.lines(
          "-identifiernamestring class " + ExcludedClass.class.getTypeName() + " {",
          "  static java.lang.String target;",
          "}");

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(ExcludedClass.class)
        .addKeepClassRulesWithAllowObfuscation(IncludedClass.class)
        .addKeepRules(identifierNameStringRule)
        .compile()
        .inspect(
            inspector -> assertThat(inspector.clazz(IncludedClass.class), isPresentAndRenamed()))
        .run(parameters.getRuntime(), ExcludedClass.class)
        .assertSuccessWithEmptyOutput();
  }

  @Test
  public void testR8Partial() throws Exception {
    testForR8Partial(parameters)
        .addR8IncludedClasses(IncludedClass.class)
        .addR8ExcludedClasses(ExcludedClass.class)
        .addKeepClassRulesWithAllowObfuscation(IncludedClass.class)
        .addKeepRules(identifierNameStringRule)
        .compile()
        .inspect(
            inspector -> assertThat(inspector.clazz(IncludedClass.class), isPresentAndNotRenamed()))
        .run(parameters.getRuntime(), ExcludedClass.class)
        .assertSuccessWithEmptyOutput();
  }

  static class ExcludedClass {

    static String target =
        "com.android.tools.r8.partial.PartialCompilationIdentifierNameStringFieldTest$IncludedClass";

    public static void main(String[] args) throws Exception {
      String className = System.currentTimeMillis() > 0 ? target : args[0];
      Class.forName(className);
    }
  }

  static class IncludedClass {}
}
