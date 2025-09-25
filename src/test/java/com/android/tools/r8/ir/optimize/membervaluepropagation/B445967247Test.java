// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.membervaluepropagation;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class B445967247Test extends TestBase {

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
        .assertSuccessWithOutputLines("[Hello, world!]");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepClassAndMembersRules(Main.class)
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/445967247): Fixme.
        .assertSuccessWithOutputLines("[]");
  }

  static class Main {

    public static void main(String[] args) {
      A a = new A(new String[] {"Hello, world!"});
      print(a);
    }

    static void print(A a) {
      System.out.println(Arrays.toString(a.f));
    }
  }

  static class A {

    static final String[] EMPTY_ARRAY = new String[0];

    String[] f;

    A(String[] f) {
      this();
      this.f = f;
    }

    @NeverInline
    A() {
      f = EMPTY_ARRAY;
    }
  }
}
