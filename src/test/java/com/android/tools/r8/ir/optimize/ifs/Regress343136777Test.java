// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.ifs;

import static org.junit.Assert.assertThrows;

import com.android.tools.r8.CompilationFailedException;
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
public class Regress343136777Test extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static final String EXPECTED_OUTPUT = StringUtils.lines("Hello, world!");

  @Test
  public void testD8Debug() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .setMinApi(parameters)
        .debug()
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testD8Release() throws Exception {
    parameters.assumeDexRuntime();
    // TODO(b/343136777: This should not fail.
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForD8(parameters.getBackend())
                .addProgramClasses(TestClass.class)
                .setMinApi(parameters)
                .release()
                .compile()
                .run(parameters.getRuntime(), TestClass.class)
                .assertSuccessWithOutput(EXPECTED_OUTPUT));
  }

  @Test
  public void testR8() throws Exception {
    // TODO(b/343136777: This should not fail.
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForR8(parameters.getBackend())
                .addProgramClasses(TestClass.class)
                .setMinApi(parameters)
                .addKeepMainRule(TestClass.class)
                .compile()
                .run(parameters.getRuntime(), TestClass.class)
                .assertSuccessWithOutput(EXPECTED_OUTPUT));
  }

  static class TestClass {
    /*
      // This is the original code from b/343136777 failing the D8 release build.

      class b {
        ArrayList<String> A = new ArrayList<>();
      }

      public static StackTraceElement getCaller(b pool, StackTraceElement[] callers, int start) {
        StackTraceElement targetStackTrace = null;
        boolean shouldTrace = false;
        int i = start;
        while (i < callers.length) {
          StackTraceElement caller = callers[i];
          boolean isLogMethod = false;
          for (String className : pool.A) {
            if (caller.getClassName().equals(className)) {
              isLogMethod = true;
              break;
            }
          }
          if (shouldTrace && !isLogMethod) {
            targetStackTrace = caller;
            break;
          }
          shouldTrace = isLogMethod;
          i++;
        }
        return targetStackTrace;
      }
    */

    public static void m() {
      boolean a = false;
      for (int i = 0; i < 2; i++) {
        boolean b = false;
        for (int j = 0; j < 2; j++) {
          if (i == j) {
            b = true;
            break;
          }
        }
        if (a && b) {
          break;
        }
        a = b;
      }
    }

    public static void main(String[] args) {
      m();
      System.out.println("Hello, world!");
    }
  }
}
