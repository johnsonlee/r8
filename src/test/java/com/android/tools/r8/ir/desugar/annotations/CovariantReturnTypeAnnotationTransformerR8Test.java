// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.annotations;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static com.android.tools.r8.utils.codeinspector.AssertUtils.assertFailsCompilation;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.equalTo;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompilerBuilder.DiagnosticsConsumer;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.errors.NonKeptMethodWithCovariantReturnTypeAnnotationDiagnostic;
import com.android.tools.r8.ir.desugar.annotations.CovariantReturnType.CovariantReturnTypes;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CovariantReturnTypeAnnotationTransformerR8Test extends TestBase {

  private static final String covariantReturnTypeDescriptor =
      "Ldalvik/annotation/codegen/CovariantReturnType;";
  private static final String covariantReturnTypesDescriptor =
      "Ldalvik/annotation/codegen/CovariantReturnType$CovariantReturnTypes;";

  private static final Map<String, String> descriptorTransformation =
      ImmutableMap.of(
          descriptor(com.android.tools.r8.ir.desugar.annotations.version2.B.class),
          descriptor(B.class),
          descriptor(com.android.tools.r8.ir.desugar.annotations.version2.C.class),
          descriptor(C.class),
          descriptor(com.android.tools.r8.ir.desugar.annotations.version2.E.class),
          descriptor(E.class),
          descriptor(com.android.tools.r8.ir.desugar.annotations.version2.F.class),
          descriptor(F.class),
          descriptor(CovariantReturnType.class),
          covariantReturnTypeDescriptor,
          descriptor(CovariantReturnTypes.class),
          covariantReturnTypesDescriptor);

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withMaximumApiLevel().build();
  }

  @Test
  public void testDontObfuscateDontOptimizeDontShrink() throws Exception {
    R8TestCompileResult r8CompileResult =
        compileWithR8(
            testBuilder -> testBuilder.addDontObfuscate().addDontOptimize().addDontShrink());
    testOnRuntime(r8CompileResult);
  }

  @Test
  public void testUnconditionalKeepAllPublicMethods() throws Exception {
    R8TestCompileResult r8CompileResult =
        compileWithR8(
            testBuilder -> testBuilder.addKeepRules("-keep public class * { public <methods>; }"));
    testOnRuntime(r8CompileResult);
  }

  @Test
  public void testUnconditionalKeepAllPublicMethodsAllowObfuscation() throws Exception {
    assertFailsCompilation(
        () ->
            compileWithR8(
                testBuilder ->
                    testBuilder.addKeepRules(
                        "-keep,allowobfuscation public class * { public <methods>; }"),
                this::inspectDiagnostics));
  }

  @Test
  public void testUnconditionalKeepAllPublicMethodsAllowOptimization() throws Exception {
    assertFailsCompilation(
        () ->
            compileWithR8(
                testBuilder ->
                    testBuilder.addKeepRules(
                        "-keep,allowoptimization public class * { public <methods>; }"),
                this::inspectDiagnostics));
  }

  @Test
  public void testConditionalKeepAllPublicMethods() throws Exception {
    assertFailsCompilation(
        () ->
            compileWithR8(
                testBuilder ->
                    testBuilder.addKeepRules(
                        "-if public class * -keep class <1> { public <methods>; }",
                        "-keep public class *"),
                this::inspectDiagnostics));
  }

  private R8TestCompileResult compileWithR8(
      ThrowableConsumer<? super R8FullTestBuilder> configuration) throws Exception {
    return compileWithR8(configuration, TestDiagnosticMessages::assertNoMessages);
  }

  private R8TestCompileResult compileWithR8(
      ThrowableConsumer<? super R8FullTestBuilder> configuration,
      DiagnosticsConsumer<?> diagnosticsConsumer)
      throws Exception {
    return testForR8(parameters.getBackend())
        .addProgramClasses(A.class, D.class)
        .addProgramClassFileData(
            transformer(com.android.tools.r8.ir.desugar.annotations.version2.B.class)
                .replaceClassDescriptorInAnnotations(descriptorTransformation)
                .replaceClassDescriptorInMethodInstructions(descriptorTransformation)
                .setClassDescriptor(descriptor(B.class))
                .transform(),
            transformer(com.android.tools.r8.ir.desugar.annotations.version2.C.class)
                .replaceClassDescriptorInAnnotations(descriptorTransformation)
                .replaceClassDescriptorInMethodInstructions(descriptorTransformation)
                .setClassDescriptor(descriptor(C.class))
                .transform(),
            transformer(com.android.tools.r8.ir.desugar.annotations.version2.E.class)
                .replaceClassDescriptorInAnnotations(descriptorTransformation)
                .replaceClassDescriptorInMethodInstructions(descriptorTransformation)
                .setClassDescriptor(descriptor(E.class))
                .transform(),
            transformer(com.android.tools.r8.ir.desugar.annotations.version2.F.class)
                .replaceClassDescriptorInAnnotations(descriptorTransformation)
                .replaceClassDescriptorInMethodInstructions(descriptorTransformation)
                .setClassDescriptor(descriptor(F.class))
                .transform(),
            transformer(CovariantReturnType.class)
                .replaceClassDescriptorInAnnotations(descriptorTransformation)
                .setClassDescriptor(covariantReturnTypeDescriptor)
                .transform(),
            transformer(CovariantReturnTypes.class)
                .replaceClassDescriptorInAnnotations(descriptorTransformation)
                .replaceClassDescriptorInMembers(
                    descriptor(CovariantReturnType.class), covariantReturnTypeDescriptor)
                .setClassDescriptor(covariantReturnTypesDescriptor)
                .transform())
        .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
        .addOptionsModification(options -> options.processCovariantReturnTypeAnnotations = true)
        .apply(configuration)
        .setMinApi(parameters)
        .compileWithExpectedDiagnostics(diagnosticsConsumer);
  }

  private void inspectDiagnostics(TestDiagnosticMessages diagnostics) throws Exception {
    List<MethodReference> methods =
        ImmutableList.of(
            Reference.methodFromMethod(B.class.getDeclaredMethod("method")),
            Reference.methodFromMethod(C.class.getDeclaredMethod("method")),
            Reference.methodFromMethod(F.class.getDeclaredMethod("method")));
    List<String> messages =
        ListUtils.map(
            methods,
            method ->
                "Methods with @CovariantReturnType annotations should be kept, but was not: "
                    + MethodReferenceUtils.toSourceString(method));
    List<Matcher<Diagnostic>> matchers =
        ListUtils.map(
            messages,
            message ->
                allOf(
                    diagnosticType(NonKeptMethodWithCovariantReturnTypeAnnotationDiagnostic.class),
                    diagnosticMessage(equalTo(message))));
    diagnostics.assertErrorsMatch(matchers);
  }

  private void testOnRuntime(R8TestCompileResult r8CompileResult) throws Exception {
    testForD8()
        .addProgramClasses(Client.class)
        .addClasspathClasses(A.class, B.class, C.class, D.class, E.class, F.class)
        .setMinApi(parameters)
        .compile()
        .addRunClasspathFiles(r8CompileResult.writeToZip())
        .run(parameters.getRuntime(), Client.class)
        .assertSuccessWithOutputLines("a=A", "b=B", "c=C", "d=F", "e=F", "f=F");
  }
}
