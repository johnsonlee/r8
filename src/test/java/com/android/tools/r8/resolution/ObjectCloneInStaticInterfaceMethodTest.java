// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resolution;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Opcodes;

// Regression test to check Object.clone remains protected access (related to b/342802978).
@RunWith(Parameterized.class)
public class ObjectCloneInStaticInterfaceMethodTest extends TestBase {

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
        .apply(this::addProgramInputs)
        .run(parameters.getRuntime(), TestClass.class)
        .apply(this::checkOutput);
  }

  private void addProgramInputs(TestBuilder<? extends SingleTestRunResult<?>, ?> builder)
      throws Exception {
    builder
        .addProgramClasses(TestClass.class)
        .addProgramClassFileData(
            transformer(I.class)
                .transformMethodInsnInMethod(
                    "myClone",
                    (opcode, owner, name, descriptor, isInterface, visitor) -> {
                      assertEquals("[Ljava/lang/String;", owner);
                      String newOwner = binaryName(Object.class);
                      visitor.visitTypeInsn(Opcodes.CHECKCAST, binaryName(Object.class));
                      visitor.visitMethodInsn(
                          Opcodes.INVOKESPECIAL, newOwner, name, descriptor, isInterface);
                    })
                .transform());
  }

  @Test
  public void testDexNoDesugar() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8(parameters.getBackend())
        .apply(this::addProgramInputs)
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
        .apply(this::addProgramInputs)
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(
            parameters.isDexRuntime() || parameters.isCfRuntime(CfVm.JDK8),
            this::checkOutput,
            r -> r.assertFailureWithErrorThatThrows(VerifyError.class));
  }

  private void checkOutput(SingleTestRunResult<?> r) {
    r.assertFailureWithErrorThatThrows(IllegalAccessError.class);
  }

  interface I {

    static String[] myClone(String[] strings) {
      // Will be rewritten to invoke-special call to Object.clone().
      return strings.clone();
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(I.myClone(args).length);
    }
  }
}
