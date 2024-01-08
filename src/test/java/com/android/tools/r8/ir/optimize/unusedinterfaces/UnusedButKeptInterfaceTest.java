// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.unusedinterfaces;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/* Regression test for b/318787479 */
@RunWith(Parameterized.class)
public class UnusedButKeptInterfaceTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("A: I", "B:", "C: I");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public UnusedButKeptInterfaceTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(UnusedButKeptInterfaceTest.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(UnusedButKeptInterfaceTest.class)
        .addKeepMainRule(TestClass.class)
        .addKeepClassRules(I.class, A.class, B.class, C.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  interface I {}

  static class A implements I {}

  static class B extends A {}

  static class C extends A implements I {}

  static class TestClass {

    static String innerName(Class<?> clazz) {
      String typeName = clazz.getName();
      return typeName.substring(typeName.lastIndexOf('$') + 1);
    }

    public static void main(String[] args) {
      for (Class<?> c : Arrays.asList(A.class, B.class, C.class)) {
        System.out.print(innerName(c) + ":");
        for (Class<?> iface : c.getInterfaces()) {
          System.out.print(" " + innerName(iface));
        }
        System.out.println();
      }
    }
  }
}
