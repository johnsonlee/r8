// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resolution;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

// Regression test for b/342802978
@RunWith(Parameterized.class)
public class ArrayCloneInDefaultInterfaceMethodTest extends TestBase {

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
  public void testDexNoDesugar() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8(parameters.getBackend())
        .addProgramClasses(I.class, A.class, B.class, TestClass.class)
        .setMinApi(parameters)
        .disableDesugaring()
        .run(parameters.getRuntime(), TestClass.class)
        .apply(this::checkOutput);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
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
      return strings.clone();
    }
  }

  static class A implements I {}

  static class B implements I {

    @Override
    public String[] myClone(String[] strings) {
      return new String[] {"boo!"};
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      // If the workaround is disabled and the code allocates "new B()" here in place of "null"
      // then ART 14 no longer fails!
      I i = System.nanoTime() > 0 ? new A() : null;
      System.out.println(i.myClone(args).length);
    }
  }
}
