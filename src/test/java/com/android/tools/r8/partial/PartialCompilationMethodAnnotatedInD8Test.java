// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PartialCompilationMethodAnnotatedInD8Test extends TestBase {

  public static String EXPECTED_OUTPUT = StringUtils.lines("1");

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  // Test with min API level 24.
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntimesStartingFromIncluding(Version.V7_0_0)
        .withApiLevel(AndroidApiLevel.N)
        .build();
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClasses(Annotation.class, AnnotatedClass.class, Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClasses(Annotation.class, AnnotatedClass.class, Main.class)
        .addKeepClassRulesWithAllowObfuscation(Annotation.class)
        .addKeepMethodRules(
            Reference.methodFromMethod(AnnotatedClass.class.getDeclaredMethod("method")))
        .addKeepRuntimeVisibleAnnotations()
        .addKeepMainRule(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8Partial() throws Exception {
    testForR8Partial(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClasses(Annotation.class, AnnotatedClass.class, Main.class)
        .addKeepMainRule(Main.class)
        .setR8PartialConfiguration(
            builder -> builder.includeAll().excludeClasses(AnnotatedClass.class))
        .run(parameters.getRuntime(), Main.class, getClass().getTypeName())
        // TODO(b/376016627): Should succeed with EXPECTED_RESULT.
        .applyIf(
            parameters.isDexRuntimeVersionOlderThanOrEqual(Version.DEFAULT),
            r ->
                r.assertStderrMatches(
                    containsString(
                        "Unable to resolve java.lang.Class<"
                            + AnnotatedClass.class.getTypeName()
                            + "> annotation class 2")))
        .assertSuccessWithOutputLines("0");
  }

  @Retention(RetentionPolicy.RUNTIME)
  public @interface Annotation {} // Compiled with R8

  public static class AnnotatedClass { // Compiled with D8

    @Annotation
    void method() throws Exception {
      System.out.println(AnnotatedClass.class.getDeclaredMethod("method").getAnnotations().length);
    }
  }

  public static class Main {

    public static void main(String[] args) throws Exception {
      new AnnotatedClass().method();
    }
  }
}
