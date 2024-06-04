// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resolution;

import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

// Regression test for b/342802978, but with R8 giving rise to the issue after inlining.
@RunWith(Parameterized.class)
public class ArrayCloneInStaticInterfaceMethodAfterMethodInliningTest extends TestBase {

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
        .addProgramClasses(I.class, A.class, TestClass.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("0");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .setMinApi(parameters)
        .addKeepMainRule(TestClass.class)
        .addProgramClasses(I.class, A.class, TestClass.class)
        .run(parameters.getRuntime(), TestClass.class)
        .apply(this::checkOutput);
  }

  private void checkOutput(SingleTestRunResult<?> r) {
    r.assertSuccessWithOutputLines("0");
  }

  interface I {

    static String[] myClone(String[] strings) {
      try {
        return A.inlinedClone(strings);
      } catch (RuntimeException e) {
        // Extra code to avoid simple inlining.
        System.out.println("Unexpected exception: " + e);
        throw e;
      }
    }
  }

  static class A {

    public static String[] inlinedClone(String[] strings) {
      return strings.clone();
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      int count = 0;
      // Repeated calls to avoid single-caller inlining of the interface method.
      count += I.myClone(args).length;
      count += I.myClone(args).length;
      count += I.myClone(args).length;
      count += I.myClone(args).length;
      count += I.myClone(args).length;
      System.out.println(count);
    }
  }
}
