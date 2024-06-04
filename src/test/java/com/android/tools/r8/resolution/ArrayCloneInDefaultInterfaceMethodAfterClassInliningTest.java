// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resolution;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

// Regression test for b/342802978 after R8 class inlining.
@RunWith(Parameterized.class)
public class ArrayCloneInDefaultInterfaceMethodAfterClassInliningTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return TestParameters.builder()
        .withAllRuntimes()
        .withApiLevel(apiLevelWithDefaultInterfaceMethodsSupport())
        .build();
  }

  @Parameter public TestParameters parameters;

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(I.class, A.class, B.class, TestClass.class)
        .run(parameters.getRuntime(), TestClass.class)
        .apply(this::checkOutput);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .addKeepMainRule(TestClass.class)
        .addKeepClassAndMembersRules(I.class)
        .addProgramClasses(I.class, A.class, B.class, TestClass.class)
        .run(parameters.getRuntime(), TestClass.class)
        .apply(this::checkOutput);
  }

  private void checkOutput(SingleTestRunResult<?> r) {
    r.assertSuccessWithOutputLines("0");
  }

  interface I {

    default String[] myClone(String[] strings) {
      return new B().myClone(strings);
    }
  }

  static class A implements I {}

  static class B {

    @NeverInline
    public String[] myClone(String[] strings) {
      return strings.clone();
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      I i = System.nanoTime() > 0 ? new A() : null;
      System.out.println(i.myClone(args).length);
    }
  }
}
