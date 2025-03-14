// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.instanceofremoval;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class UnusedInstanceOfTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().withPartialCompilation().build();
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters)
        .addProgramClasses(Main.class)
        .release()
        .compile()
        .inspect(
            inspector -> {
              MethodSubject testMethod =
                  inspector.clazz(Main.class).uniqueMethodWithOriginalName("test");
              assertFalse(hasInstanceOf(testMethod, Main.class));
              assertTrue(hasInstanceOf(testMethod, Missing.class));
              assertTrue(hasInstanceOf(testMethod, String.class));
            });
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addProgramClasses(Main.class)
        .addKeepAllClassesRule()
        .addDontWarn(Missing.class)
        .compile()
        .inspect(
            inspector -> {
              MethodSubject testMethod =
                  inspector.clazz(Main.class).uniqueMethodWithOriginalName("test");
              assertThat(testMethod, isPresent());
              assertFalse(hasInstanceOf(testMethod, Main.class));
              assertTrue(hasInstanceOf(testMethod, Missing.class));
              assertTrue(
                  !hasInstanceOf(testMethod, String.class)
                      || parameters.getPartialCompilationTestParameters().isRandom());
            });
  }

  private boolean hasInstanceOf(MethodSubject method, Class<?> clazz) {
    return method.streamInstructions().anyMatch(i -> i.isInstanceOf(clazz.getTypeName()));
  }

  static class Main {

    static void test(Object o) {
      boolean unused1 = o instanceof Main;
      boolean unused2 = o instanceof Missing;
      boolean unused3 = o instanceof String;
    }
  }

  static class Missing {}
}
