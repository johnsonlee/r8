// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PartialCompilationWithDefaultInterfaceMethodTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesAndAllApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    // TODO(b/388763735): Enable for all API levels.
    assumeTrue(parameters.canUseDefaultAndStaticInterfaceMethods());
    testForR8Partial(parameters.getBackend())
        .addInnerClasses(getClass())
        .addR8IncludedClasses(I.class, J.class)
        .addR8ExcludedClasses(Main.class, A.class)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  // D8

  static class Main {

    public static void main(String[] args) {
      I i = new A();
      i.m();
    }
  }

  static class A implements J {}

  // R8

  interface I {

    void m();
  }

  interface J extends I {

    @Override
    default void m() {
      System.out.println("Hello, world!");
    }
  }
}
