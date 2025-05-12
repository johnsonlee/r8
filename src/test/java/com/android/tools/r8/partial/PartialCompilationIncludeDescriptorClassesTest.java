// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PartialCompilationIncludeDescriptorClassesTest extends TestBase {

  private static final String rule =
      StringUtils.lines(
          "-keep,includedescriptorclasses class " + ExcludedClass.class.getTypeName() + " {",
          "  public static void m(...);",
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
        .addKeepRules(rule)
        .compile()
        .inspect(this::inspect);
  }

  @Test
  public void testR8Partial() throws Exception {
    testForR8Partial(parameters)
        .addR8IncludedClasses(IncludedClass.class)
        .addR8ExcludedClasses(ExcludedClass.class)
        .addKeepRules(rule)
        .compile()
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject includedClassSubject = inspector.clazz(IncludedClass.class);
    assertThat(includedClassSubject, isPresentAndNotRenamed());

    MethodSubject methodSubject =
        inspector.clazz(ExcludedClass.class).uniqueMethodWithOriginalName("m");
    assertThat(methodSubject, isPresent());
    assertEquals(includedClassSubject.asTypeSubject(), methodSubject.getParameter(0));
  }

  static class ExcludedClass {

    public static void m(IncludedClass includedClass) {}
  }

  static class IncludedClass {}
}
