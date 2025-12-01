// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugaring.interfacemethods;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SuperClassNotFoundInterfaceMethodDesugaringTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesAndAllApiLevels().build();
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters)
        .addProgramClasses(I.class, J.class, A.class, Main.class)
        .release()
        .compile()
        .addRunClasspathClasses(Missing.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("I.m()");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(I.class, J.class, A.class, Main.class)
        .addKeepMainRule(Main.class)
        .addDontWarn(Missing.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .addRunClasspathClasses(Missing.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("I.m()");
  }

  static class Main {

    public static void main(String[] args) {
      new A().callM();
    }
  }

  interface I {
    @NeverInline
    default void m() {
      System.out.println("I.m()");
    }
  }

  interface J extends I {}

  public static class Missing {}

  @NeverClassInline
  static class A extends Missing implements J {

    @NeverInline
    void callM() {
      J.super.m();
    }
  }
}
