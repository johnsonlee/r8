// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

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
public class PartialCompilationIdentifierNameStringMethodTest extends TestBase {

  private static final String identifierNameStringRule =
      StringUtils.lines(
          "-identifiernamestring class " + ExcludedClass.class.getTypeName() + " {",
          "  static void foo(java.lang.String, java.lang.String);",
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
        .run(parameters.getRuntime(), ExcludedClass.class)
        .assertSuccessWithEmptyOutput();
  }

  static class ExcludedClass {

    public static void main(String[] args) throws Exception {
      String target =
          "com.android.tools.r8.partial.PartialCompilationIdentifierNameStringMethodTest$IncludedClass";
      foo(target, null);
    }

    static void foo(String a, String b) throws Exception {
      Class.forName(a);
    }
  }

  static class IncludedClass {}
}
