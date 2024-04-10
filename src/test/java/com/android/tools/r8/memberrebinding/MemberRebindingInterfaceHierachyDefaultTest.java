// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.memberrebinding;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

// This is a regression test for b/328859009.
@RunWith(Parameterized.class)
public class MemberRebindingInterfaceHierachyDefaultTest extends TestBase {

  @Parameter(0)
  public static TestParameters parameters;

  @Parameter(1)
  public static boolean dontOptimize;

  @Parameters(name = "{0}, dontOptimize = {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build(),
        BooleanUtils.values());
  }

  private static final String EXPECTED_OUTPUT = StringUtils.lines("I::m", "TestClass::m");

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .addKeepMainRule(TestClass.class)
        // Issue b/328859009 failed in DEBUG mode. Use -dontoptimize to get the same effect.
        .applyIf(dontOptimize, TestShrinkerBuilder::addDontOptimize)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  interface I {
    default void m() {
      System.out.println("I::m");
    }
  }

  interface J extends I {}

  interface K extends J {}

  static class A implements K {
    public void m() {
      K.super.m();
    }
  }

  static class B implements J {}

  static class TestClass {

    public static void m(J j) {
      System.out.println("TestClass::m");
    }

    public static void main(String[] args) {
      new A().m();
      m(new B());
    }
  }
}
