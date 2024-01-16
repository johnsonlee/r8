// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.rewrite.arrays;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NewArrayOfMissingTypeWithCatchHandlerTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithEmptyOutput();
  }

  @Test
  public void testD8Release() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addProgramClasses(Main.class)
        .release()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithEmptyOutput();
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .addKeepMainRule(Main.class)
        .addDontWarn(Missing.class)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithEmptyOutput();
  }

  static class Main {

    public static void main(String[] args) {
      // Moving the array creation down to the last array-put or the first non-array-put user would
      // change semantics as the code then no longer catches NoClassDefFoundError.
      Missing[] array;
      try {
        array = new Missing[3];
      } catch (NoClassDefFoundError e) {
        return;
      }
      array[0] = null;
      array[1] = null;
      array[2] = null;
      System.out.println(Arrays.toString(array));
    }
  }

  static class Missing {}
}
