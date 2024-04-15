// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import com.android.tools.r8.jasmin.JasminTestBase;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.UnverifiableCfCodeDiagnostic;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class B116282409 extends JasminTestBase {

  private static List<byte[]> programClassFileData;

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean enableVerticalClassMerging;

  @Rule public ExpectedException exception = ExpectedException.none();

  @Parameters(name = "{0}, vertical class merging: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  @BeforeClass
  public static void setup() throws Exception {
    JasminBuilder jasminBuilder = new JasminBuilder();

    // Create a class A with a default constructor that prints "In A.<init>()".
    ClassBuilder classBuilder = jasminBuilder.addClass("A");
    classBuilder.addMethod(
        "public",
        "<init>",
        ImmutableList.of(),
        "V",
        ".limit stack 2",
        ".limit locals 1",
        "aload_0",
        "invokespecial java/lang/Object/<init>()V",
        "getstatic java/lang/System/out Ljava/io/PrintStream;",
        "ldc \"In A.<init>()\"",
        "invokevirtual java/io/PrintStream/println(Ljava/lang/String;)V",
        "return");

    // Create a class B that inherits from A.
    classBuilder = jasminBuilder.addClass("B", "A");

    // Also add a simple method that the class inliner would inline.
    classBuilder.addVirtualMethod(
        "m", "I", ".limit stack 5", ".limit locals 5", "bipush 42", "ireturn");

    // Add a test class that initializes an instance of B using A.<init>.
    classBuilder = jasminBuilder.addClass("TestClass");
    classBuilder.addMainMethod(
        ".limit stack 5",
        ".limit locals 5",
        "getstatic java/lang/System/out Ljava/io/PrintStream;",
        "new B",
        "dup",
        "invokespecial A/<init>()V", // NOTE: usually B.<init>
        "invokevirtual B/m()I",
        "invokevirtual java/io/PrintStream/println(I)V",
        "return");

    programClassFileData = jasminBuilder.buildClasses();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClassFileData(programClassFileData)
        .run(parameters.getRuntime(), "TestClass")
        .assertFailureWithErrorThatThrows(VerifyError.class)
        .assertFailureWithErrorThatMatches(containsString("Call to wrong initialization method"));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(programClassFileData)
        .addKeepMainRule("TestClass")
        .addOptionsModification(
            options ->
                options.getVerticalClassMergerOptions().setEnabled(enableVerticalClassMerging))
        .allowDiagnosticWarningMessages()
        .setMinApi(parameters)
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics.assertWarningsMatch(
                    allOf(
                        diagnosticType(UnverifiableCfCodeDiagnostic.class),
                        diagnosticMessage(
                            containsString(
                                "Constructor mismatch, expected constructor from B, but was"
                                    + " A.<init>()")))))
        .run(parameters.getRuntime(), "TestClass")
        .applyIf(
            (parameters.isCfRuntime() || parameters.getDexRuntimeVersion().isDalvik())
                && !enableVerticalClassMerging,
            runResult ->
                runResult
                    .assertFailureWithErrorThatThrows(VerifyError.class)
                    .assertFailureWithErrorThatMatches(
                        containsString(
                            parameters.isCfRuntime()
                                ? "Call to wrong initialization method"
                                : "VFY: invoke-direct <init> on super only allowed for 'this' in"
                                    + " <init>")),
            runResult -> runResult.assertSuccessWithOutputLines("In A.<init>()", "42"));
  }
}
