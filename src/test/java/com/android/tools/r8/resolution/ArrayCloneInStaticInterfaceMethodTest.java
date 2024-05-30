// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resolution;

import static org.junit.Assert.assertEquals;

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
public class ArrayCloneInStaticInterfaceMethodTest extends TestBase {

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
        .addProgramClasses(I.class, TestClass.class)
        .run(parameters.getRuntime(), TestClass.class)
        .apply(this::checkOutput);
  }

  @Test
  public void testArrayReturn() throws Exception {
    // This just checks that the array clone must have exactly java.lang.Object as return type.
    testForRuntime(parameters)
        .addProgramClassFileData(
            transformer(I.class)
                .transformMethodInsnInMethod(
                    "myClone",
                    (opcode, owner, name, descriptor, isInterface, visitor) -> {
                      assertEquals("()Ljava/lang/Object;", descriptor);
                      String newDescriptor = "()[Ljava/lang/Object;";
                      visitor.visitMethodInsn(opcode, owner, name, newDescriptor, isInterface);
                    })
                .transform())
        .addProgramClasses(TestClass.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertFailureWithErrorThatThrows(NoSuchMethodError.class);
  }

  @Test
  public void testDexNoDesugar() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters.getBackend())
        .addProgramClasses(I.class, TestClass.class)
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
        .addProgramClasses(I.class, TestClass.class)
        .run(parameters.getRuntime(), TestClass.class)
        .apply(this::checkOutput);
  }

  private void checkOutput(SingleTestRunResult<?> r) {
    r.assertSuccessWithOutputLines("0");
  }

  interface I {

    static String[] myClone(String[] strings) {
      return strings.clone();
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(I.myClone(args).length);
    }
  }
}
