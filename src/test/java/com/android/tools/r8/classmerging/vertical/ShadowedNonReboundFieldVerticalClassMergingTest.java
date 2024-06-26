// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.classmerging.vertical;

import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ShadowedNonReboundFieldVerticalClassMergingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("1", "2", "2");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addVerticallyMergedClassesInspector(
            inspector -> inspector.assertMergedIntoSubtype(B.class).assertNoOtherClassesMerged())
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("1", "2", "2");
  }

  static class Main {

    public static void main(String[] args) {
      C c = System.currentTimeMillis() > 0 ? new C(1, 2) : new C(3, 4);
      System.out.println(c.f);
      System.out.println(c.a());
      System.out.println(c.b());
    }
  }

  @NoVerticalClassMerging
  static class A {

    int f;

    A(int f) {
      this.f = f;
    }

    int a() {
      return f;
    }
  }

  static class B extends A {

    B(int f) {
      super(f);
    }

    int b() {
      return f;
    }
  }

  static class C extends B {

    int f;

    C(int f, int g) {
      super(g);
      this.f = f;
    }
  }
}
