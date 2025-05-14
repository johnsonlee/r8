// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PartialCompilationIdentifierNameStringFieldTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean specific;

  @Parameters(name = "{0}, specific:{1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(ExcludedClass.class)
        .addKeepClassRulesWithAllowObfuscation(IncludedClass.class)
        .addKeepRules(getIdentifierNameStringRule())
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
        .addKeepRules(getIdentifierNameStringRule())
        .compile()
        .inspect(
            inspector -> assertThat(inspector.clazz(IncludedClass.class), isPresentAndNotRenamed()))
        .run(parameters.getRuntime(), ExcludedClass.class)
        .assertSuccessWithEmptyOutput();
  }

  private String getIdentifierNameStringRule() {
    String classNamePattern = specific ? ExcludedClass.class.getTypeName() : "*";
    return StringUtils.lines(
        "-identifiernamestring class " + classNamePattern + " {",
        "  static java.lang.String target;",
        "}");
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
