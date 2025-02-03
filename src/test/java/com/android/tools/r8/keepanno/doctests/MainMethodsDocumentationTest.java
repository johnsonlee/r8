// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.doctests;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.keepanno.annotations.KeepEdge;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.KeepTarget;
import com.android.tools.r8.keepanno.annotations.MethodAccessFlags;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MainMethodsDocumentationTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("Hello I'm kept!");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withMaximumApiLevel().build();
  }

  public MainMethodsDocumentationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(getInputClasses())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testWithRuleExtraction() throws Exception {
    testForR8(parameters.getBackend())
        .enableExperimentalKeepAnnotations()
        .addProgramClasses(getInputClasses())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  public List<Class<?>> getInputClasses() {
    return ImmutableList.of(TestClass.class, SomeClass.class);
  }

  /* INCLUDE DOC: KeepMainMethods
  For example, to keep all main methods in the program one could use:
  INCLUDE END */

  static
  // INCLUDE CODE: KeepMainMethods
  @KeepEdge(
      consequences = {
        @KeepTarget(
            kind = KeepItemKind.CLASS_AND_MEMBERS,
            methodName = "main",
            methodReturnType = "void",
            methodParameters = {"java.lang.String[]"},
            methodAccess = {MethodAccessFlags.PUBLIC, MethodAccessFlags.STATIC})
      })
  public class SomeClass {
    // ...
  }

  // INCLUDE END

  static class TestClass {

    public static void main(String[] args) throws Exception {
      System.out.println("Hello I'm kept!");
    }
  }
}
