// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.invokespecial;

import static com.android.tools.r8.utils.DescriptorUtils.getBinaryNameFromJavaType;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;

import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvokeSpecialToImmediateInterfaceTest extends TestBase {

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
        .addProgramClasses(I.class, Main.class)
        .addProgramClassFileData(getClassWithTransformedInvoked())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  @Test
  public void testD8Release() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters.getBackend())
        .addProgramClasses(I.class, Main.class)
        .addProgramClassFileData(getClassWithTransformedInvoked())
        .release()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/313065227): Should succeed.
        .applyIf(
            parameters.getDexRuntimeVersion().isEqualToOneOf(Version.V5_1_1, Version.V6_0_1),
            runResult -> runResult.assertFailureWithErrorThatMatches(containsString("SIGSEGV")),
            runResult -> runResult.assertFailureWithErrorThatThrows(NoSuchMethodError.class));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(I.class, Main.class)
        .addProgramClassFileData(getClassWithTransformedInvoked())
        .addKeepMainRule(Main.class)
        // Illegal invoke-super to interface method.
        .applyIf(
            parameters.isDexRuntime()
                && parameters.getApiLevel().isLessThanOrEqualTo(AndroidApiLevel.K),
            R8TestBuilder::allowDiagnosticWarningMessages)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/313065227): Should succeed.
        .applyIf(
            parameters.isCfRuntime(),
            runResult -> runResult.assertFailureWithErrorThatThrows(NoSuchMethodError.class),
            parameters.isDexRuntime() && parameters.getDexRuntimeVersion().isDalvik(),
            runResult -> runResult.assertFailureWithErrorThatThrows(VerifyError.class),
            parameters.canUseDefaultAndStaticInterfaceMethods(),
            runResult -> runResult.assertFailureWithErrorThatThrows(NullPointerException.class),
            parameters.isDexRuntime()
                && parameters.getDexRuntimeVersion().isEqualToOneOf(Version.V5_1_1, Version.V6_0_1),
            runResult ->
                runResult.assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class),
            runResult -> runResult.assertFailureWithErrorThatThrows(AbstractMethodError.class));
  }

  private byte[] getClassWithTransformedInvoked() throws IOException {
    return transformer(A.class)
        .transformMethodInsnInMethod(
            "bar",
            (opcode, owner, name, descriptor, isInterface, continuation) -> {
              assertEquals(INVOKEVIRTUAL, opcode);
              assertEquals(owner, getBinaryNameFromJavaType(A.class.getTypeName()));
              continuation.visitMethodInsn(
                  INVOKESPECIAL,
                  getBinaryNameFromJavaType(A.class.getTypeName()),
                  name,
                  descriptor,
                  isInterface);
            })
        .transform();
  }

  public interface I {

    default void foo() {
      System.out.println("Hello, world!");
    }
  }

  public static class A implements I {

    public void bar() {
      foo(); // Will be rewritten to invoke-special A.foo()
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new A().bar();
    }
  }
}
