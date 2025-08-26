// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.regalloc;


import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MoveExceptionInvokeRangeTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters)
        .addInnerClasses(getClass())
        .release()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class, "a", "b", "c", "d", "e")
        .assertSuccessWithEmptyOutput();
  }

  static class Main {

    public static void main(String[] args) {
      String s0 = args[0];
      String s1 = args[1];
      String s2 = args[2];
      String s3 = args[3];
      String s4 = args[4];
      try {
        doSomething();
      } catch (Exception e) {
        handleException(s0, s1, s2, s3, s4, e);
      }
    }

    static void doSomething() {}

    static void handleException(
        String s0, String s1, String s2, String s3, String s4, Exception e) {}
  }
}
