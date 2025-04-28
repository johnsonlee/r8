// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugaring.interfacemethods;

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
public class InterfaceWithClinitAndNoStaticMethodReferencesTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("allocated A", "called A.m");

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withAllRuntimesAndApiLevels()
        .withAllApiLevelsAlsoForCf()
        .withPartialCompilation()
        .build();
  }

  @Test
  public void testDesugaring() throws Exception {
    testForDesugaring(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    parameters.assumeNoPartialCompilation("TODO");
    testForR8(parameters.getBackend())
        .addInnerClasses(InterfaceWithClinitAndNoStaticMethodReferencesTest.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  static class A {

    A() {
      System.out.println("allocated A");
    }

    void m() {
      System.out.println("called A.m");
    }
  }

  interface I {

    A a = new A();

    static void f() {
      System.out.println("unused");
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      I.a.m();
    }
  }
}
