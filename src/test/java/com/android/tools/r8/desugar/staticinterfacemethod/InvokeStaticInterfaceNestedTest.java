// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.staticinterfacemethod;

import static com.android.tools.r8.desugar.staticinterfacemethod.InvokeStaticInterfaceNestedTest.Library.foo;
import static com.android.tools.r8.utils.codeinspector.AssertUtils.assertFailsCompilationIf;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.DesugarTestConfiguration;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvokeStaticInterfaceNestedTest extends TestBase {

  private static final String UNEXPECTED_SUCCESS = "Hello World!";

  @Parameter(0)
  public boolean allowInvokeErrors;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, allow invalid invokes: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        getTestParameters()
            .withAllRuntimes()
            .withAllApiLevelsAlsoForCf()
            .withPartialCompilation()
            .build());
  }

  private void inspectRunResult(TestRunResult<?> runResult, boolean isDesugared) {
    boolean didDesugarInterfaceMethods =
        isDesugared && !parameters.canUseDefaultAndStaticInterfaceMethodsWhenDesugaring();
    if (parameters.isCfRuntime()) {
      if (parameters.getRuntime().asCf().isNewerThanOrEqual(CfVm.JDK9)) {
        // The correct expected behavior is ICCE.
        runResult.assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class);
      } else if (didDesugarInterfaceMethods) {
        runResult.assertFailureWithErrorThatThrows(NoSuchMethodError.class);
      } else {
        // Dex VMs and JDK 8 will just dispatch (this is not the intended behavior).
        runResult.assertSuccessWithOutputLines(UNEXPECTED_SUCCESS);
      }
      return;
    }
    if (parameters.canUseDefaultAndStaticInterfaceMethodsWhenDesugaring()) {
      // Dex VMs and JDK 8 will just dispatch (this is not the intended behavior).
      runResult.assertSuccessWithOutputLines(UNEXPECTED_SUCCESS);
      return;
    }
    Version version = parameters.getRuntime().asDex().getVm().getVersion();
    if (version.isOlderThanOrEqual(Version.V4_4_4)) {
      runResult.assertFailureWithErrorThatThrows(VerifyError.class);
    } else {
      runResult.assertFailureWithErrorThatThrows(NoSuchMethodError.class);
    }
  }

  @Test
  public void testDesugar() throws Exception {
    assumeFalse(allowInvokeErrors);
    testForDesugaring(parameters)
        .addProgramClassFileData(
            rewriteToUseNonInterfaceMethodReference(Main.class, "main"),
            rewriteToUseNonInterfaceMethodReference(Library.class, "foo"))
        .run(parameters.getRuntime(), Main.class)
        .apply(
            result ->
                result.applyIf(
                    DesugarTestConfiguration::isDesugared,
                    r -> inspectRunResult(r, true),
                    r -> inspectRunResult(r, false)));
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    parameters.assumeNoPartialCompilation("TODO");
    assertFailsCompilationIf(
        !allowInvokeErrors,
        () ->
            testForR8(parameters.getBackend())
                .addProgramClassFileData(
                    rewriteToUseNonInterfaceMethodReference(Main.class, "main"),
                    rewriteToUseNonInterfaceMethodReference(Library.class, "foo"))
                .addKeepAllClassesRule()
                .addOptionsModification(
                    options -> options.getTestingOptions().allowInvokeErrors = allowInvokeErrors)
                .setMinApi(parameters)
                .compile()
                .run(parameters.getRuntime(), Main.class)
                .apply(runResult -> inspectRunResult(runResult, parameters.isDexRuntime())));
  }

  private byte[] rewriteToUseNonInterfaceMethodReference(Class<?> clazz, String methodName)
      throws Exception {
    return transformer(clazz)
        .transformMethodInsnInMethod(
            methodName,
            (opcode, owner, name, descriptor, isInterface, visitor) -> {
              assertTrue(isInterface);
              visitor.visitMethodInsn(opcode, owner, name, descriptor, false);
            })
        .transform();
  }

  public interface Library {

    static void foo() {
      bar(); // <-- will be rewritten to invoke-static Library::bar();
    }

    static void bar() {
      System.out.println("Hello World!");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      foo(); // <-- will be rewritten to invoke-static Library::foo();
    }
  }
}
