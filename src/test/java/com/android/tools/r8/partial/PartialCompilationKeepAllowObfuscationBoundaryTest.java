// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PartialCompilationKeepAllowObfuscationBoundaryTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    parameters.assumeCanUseR8Partial();
    testForR8Partial(parameters.getBackend())
        .addR8IncludedClasses(IncludedClass.class)
        .addR8ExcludedClasses(Main.class)
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject includedClassSubject = inspector.clazz(IncludedClass.class);
              assertThat(includedClassSubject, isPresentAndRenamed());

              MethodSubject fooMethodSubject =
                  includedClassSubject.uniqueMethodWithOriginalName("foo");
              assertThat(fooMethodSubject, isPresentAndRenamed());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  static class Main {

    public static void main(String[] args) {
      IncludedClass.foo();
    }
  }

  static class IncludedClass {

    static void foo() {
      System.out.println("Hello, world!");
    }
  }
}
