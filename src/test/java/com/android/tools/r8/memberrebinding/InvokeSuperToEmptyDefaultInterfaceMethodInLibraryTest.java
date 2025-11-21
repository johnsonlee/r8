// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.memberrebinding;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.errors.IllegalInvokeSuperToInterfaceOnDalvikDiagnostic;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvokeSuperToEmptyDefaultInterfaceMethodInLibraryTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, A.class)
        .addLibraryClasses(Build.class, I.class)
        .addDefaultRuntimeLibrary(parameters)
        .addKeepMainRule(Main.class)
        .allowDiagnosticWarningMessages(shouldReportDiagnostic())
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics.applyIf(
                    shouldReportDiagnostic(),
                    d ->
                        d.assertWarningsMatch(
                            allOf(
                                diagnosticType(
                                    IllegalInvokeSuperToInterfaceOnDalvikDiagnostic.class),
                                diagnosticMessage(
                                    containsString(
                                        "Verification error in `"
                                            + MethodReferenceUtils.toSourceString(
                                                Reference.methodFromMethod(
                                                    A.class.getDeclaredMethod("foo")))
                                            + "`: Illegal invoke-super to interface method `"
                                            + MethodReferenceUtils.toSourceString(
                                                Reference.methodFromMethod(
                                                    I.class.getDeclaredMethod("foo")))
                                            + "` on Dalvik (Android 4).")))),
                    TestDiagnosticMessages::assertNoMessages))
        .addRunClasspathClasses(I.class)
        .addRunClasspathClassFileData(
            transformer(Build.class)
                .transformMethodInsnInMethod(
                    "hasDefaultInterfaceMethodsSupport",
                    (opcode, owner, name, descriptor, isInterface, visitor) ->
                        visitor.visitMethodInsn(
                            opcode,
                            owner,
                            parameters.hasDefaultInterfaceMethodsSupport() ? "getTrue" : "getFalse",
                            descriptor,
                            isInterface))
                .transform())
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            parameters.isDexRuntime() && parameters.getDexRuntimeVersion().isDalvik(),
            runResult -> runResult.assertFailureWithErrorThatThrows(VerifyError.class),
            runResult ->
                runResult.assertSuccessWithOutputLines(
                    parameters.hasDefaultInterfaceMethodsSupport()
                        ? ImmutableList.of("In override!", "Call!")
                        : ImmutableList.of("Skip!")));
  }

  private boolean shouldReportDiagnostic() {
    return parameters.isDexRuntime() && parameters.getApiLevel().isLessThan(AndroidApiLevel.L);
  }

  static class Main {

    public static void main(String[] args) {
      A a = new A();
      if (Build.hasDefaultInterfaceMethodsSupport()) {
        a.foo();
      } else {
        System.out.println("Skip!");
      }
    }
  }

  // Library.
  public interface I {

    default void foo() {
      System.out.println("Call!");
    }
  }

  // Library.
  public static class Build {

    public static boolean hasDefaultInterfaceMethodsSupport() {
      // Rewritten to getTrue() by transformer when the runtime supports default interface methods.
      return getFalse();
    }

    private static boolean getFalse() {
      return false;
    }

    private static boolean getTrue() {
      return true;
    }
  }

  // Program.
  static class A implements I {

    @Override
    @NeverInline
    public void foo() {
      System.out.println("In override!");
      I.super.foo();
    }
  }
}
