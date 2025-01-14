// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

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
public class DefaultInterfaceMethodWithOpenHierarchyTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    assumeTrue(parameters.canUseDefaultAndStaticInterfaceMethods());
    testForR8(parameters.getBackend())
        .addProgramClasses(I.class, J.class)
        .addKeepClassAndMembersRules(I.class)
        .addKeepClassRules(J.class)
        .setMinApi(parameters)
        .compile()
        .addRunClasspathClasses(Main.class, A.class)
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

    default void m() {
      System.out.println("Hello, world!");
    }
  }
}
