// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AbstractMethodWithOpenHierarchyTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(A.class, B.class)
        .addKeepClassAndMembersRules(A.class)
        .addKeepClassAndDefaultConstructor(B.class)
        .setMinApi(parameters)
        .compile()
        .addRunClasspathClasses(Main.class, C.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  // D8

  static class Main {

    public static void main(String[] args) {
      A a = new C();
      a.m();
    }
  }

  static class C extends B {}

  // R8

  abstract static class A {

    public abstract void m();
  }

  abstract static class B extends A {

    @Override
    public void m() {
      System.out.println("Hello, world!");
    }
  }
}
