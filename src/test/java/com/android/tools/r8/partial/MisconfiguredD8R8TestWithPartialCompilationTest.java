// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MisconfiguredD8R8TestWithPartialCompilationTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().withPartialCompilation().build();
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    try {
      testForD8(parameters.getBackend())
          .addInnerClasses(getClass())
          .release()
          .setMinApi(parameters)
          .compile();
      assertTrue(parameters.getPartialCompilationTestParameters().isNone());
    } catch (AssertionError e) {
      assertTrue(parameters.getPartialCompilationTestParameters().isSome());
    }
  }

  @Test
  public void testR8() throws Exception {
    try {
      testForR8(parameters.getBackend())
          .addInnerClasses(getClass())
          .addKeepMainRule(Main.class)
          .setMinApi(parameters)
          .compile();
      assertTrue(parameters.getPartialCompilationTestParameters().isNone());
    } catch (AssertionError e) {
      assertTrue(parameters.getPartialCompilationTestParameters().isSome());
    }
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println("Hello, world!");
    }
  }
}
