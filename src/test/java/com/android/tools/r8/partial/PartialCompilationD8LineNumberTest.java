// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import static com.android.tools.r8.naming.retrace.StackTrace.isSame;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.R8PartialTestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PartialCompilationD8LineNumberTest extends TestBase {

  private static StackTrace expectedD8StackTrace;
  private static StackTrace expectedR8StackTrace;

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @BeforeClass
  public static void setup() throws Exception {
    // Get the expected stack traces by running on the JVM.
    expectedD8StackTrace =
        testForJvm(getStaticTemp())
            .addTestClasspath()
            .run(CfRuntime.getSystemRuntime(), ExcludedClass.class)
            .assertFailureWithErrorThatThrows(RuntimeException.class)
            .map(StackTrace::extractFromJvm);
    expectedR8StackTrace =
        testForJvm(getStaticTemp())
            .addTestClasspath()
            .run(CfRuntime.getSystemRuntime(), IncludedClass.class)
            .assertFailureWithErrorThatThrows(RuntimeException.class)
            .map(StackTrace::extractFromJvm);
  }

  @Test
  public void test() throws Throwable {
    parameters.assumeR8PartialTestParameters();
    R8PartialTestCompileResult compileResult =
        testForR8Partial(parameters.getBackend())
            .addR8IncludedClasses(IncludedClass.class)
            .addR8ExcludedClasses(ExcludedClass.class)
            .addKeepMainRule(IncludedClass.class)
            .setMinApi(parameters)
            .compile()
            .inspect(
                inspector -> {
                  ClassSubject includedClass = inspector.clazz(IncludedClass.class);
                  assertThat(includedClass, isPresent());
                  assertEquals(
                      "SourceFile", includedClass.getDexProgramClass().getSourceFile().toString());

                  ClassSubject excludedClass = inspector.clazz(ExcludedClass.class);
                  assertThat(excludedClass, isPresent());
                  assertEquals(
                      "PartialCompilationD8LineNumberTest.java",
                      excludedClass.getDexProgramClass().getSourceFile().toString());
                })
            .inspectProguardMap(
                map -> {
                  assertThat(map, containsString(IncludedClass.class.getTypeName()));
                  assertThat(map, not(containsString(ExcludedClass.class.getTypeName())));
                });

    compileResult
        .run(parameters.getRuntime(), ExcludedClass.class)
        .assertFailureWithErrorThatThrows(RuntimeException.class)
        .inspectOriginalStackTrace(
            stackTrace -> assertThat(stackTrace, isSame(expectedD8StackTrace)));

    compileResult
        .run(parameters.getRuntime(), IncludedClass.class)
        .assertFailureWithErrorThatThrows(RuntimeException.class)
        .inspectOriginalStackTrace(
            stackTrace -> assertThat(stackTrace, not(isSame(expectedD8StackTrace))))
        .inspectStackTrace(stackTrace -> assertThat(stackTrace, isSame(expectedR8StackTrace)));
  }

  static class IncludedClass {

    public static void main(String[] args) {
      foo();
    }

    private static void foo() {
      throw new RuntimeException();
    }
  }

  static class ExcludedClass {

    public static void main(String[] args) {
      foo();
    }

    private static void foo() {
      throw new RuntimeException();
    }
  }
}
