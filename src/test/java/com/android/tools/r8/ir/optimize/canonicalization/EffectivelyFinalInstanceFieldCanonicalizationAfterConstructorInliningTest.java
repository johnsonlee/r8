// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.canonicalization;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoRedundantFieldLoadElimination;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Regression test for b/315877832. */
@RunWith(Parameterized.class)
public class EffectivelyFinalInstanceFieldCanonicalizationAfterConstructorInliningTest
    extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNoRedundantFieldLoadEliminationAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  static class Main {

    public static void main(String[] args) {
      Object alwaysNull = System.currentTimeMillis() > 0 ? null : new Object();
      A a = new A(alwaysNull);
      System.out.println(a);
    }
  }

  static class A {

    @NoRedundantFieldLoadElimination Object f;

    A(Object o) {
      f = o;
      if (f == null) {
        f = "Hello";
      }
      print(f);
    }

    @NeverInline
    static void print(Object o) {
      System.out.print(o);
    }

    @Override
    public String toString() {
      return ", world!";
    }
  }
}
