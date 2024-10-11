// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Regress371247958 extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withAllApiLevels().build();
  }

  public Regress371247958(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertFailureWithErrorThatThrows(RuntimeException.class);
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .applyIf(parameters.isDexRuntime(), b -> b.setMinApi(parameters))
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertFailureWithErrorThatThrows(RuntimeException.class);
  }

  public static class TestClass {
    static int throwException(RuntimeException runtimeException, boolean shouldReturnValue) {
      if (shouldReturnValue) {
        return 1;
      }
      throw runtimeException;
    }

    public static void main(String[] p) throws Exception {
      System.out.println(throwException(new RuntimeException("always thrown"), false));
    }
  }
}
