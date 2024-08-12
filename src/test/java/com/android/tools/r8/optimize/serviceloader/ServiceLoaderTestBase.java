// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.serviceloader;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethodWithName;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.DiagnosticsMatcher;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompilerBuilder.DiagnosticsConsumer;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.graph.AppServices;
import com.android.tools.r8.ir.optimize.ServiceLoaderRewriterDiagnostic;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.DataResourceConsumerForTesting;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ServiceLoaderTestBase extends TestBase {
  private static final DiagnosticsConsumer<CompilationFailedException> REWRITER_DIAGNOSTICS =
      diagnostics ->
          diagnostics
              .assertOnlyInfos()
              .assertAllInfosMatch(
                  DiagnosticsMatcher.diagnosticType(ServiceLoaderRewriterDiagnostic.class));

  protected final TestParameters parameters;
  protected final boolean enableRewriting;
  protected DataResourceConsumerForTesting dataResourceConsumer;
  protected final DiagnosticsConsumer<CompilationFailedException> expectedDiagnostics;

  public ServiceLoaderTestBase(TestParameters parameters) {
    this(parameters, true);
  }

  public ServiceLoaderTestBase(TestParameters parameters, boolean enableRewriting) {
    this.parameters = parameters;
    this.enableRewriting = enableRewriting;
    if (enableRewriting) {
      expectedDiagnostics = REWRITER_DIAGNOSTICS;
    } else {
      expectedDiagnostics = diagnostics -> diagnostics.assertNoInfos();
    }
  }

  public static long getServiceLoaderLoads(CodeInspector inspector) {
    return inspector
        .streamInstructions()
        .filter(ServiceLoaderTestBase::isServiceLoaderLoad)
        .count();
  }

  private static boolean isServiceLoaderLoad(InstructionSubject instruction) {
    return instruction.isInvokeStatic()
        && instruction.getMethod().qualifiedName().contains("ServiceLoader.load");
  }

  public static void verifyNoClassLoaders(CodeInspector inspector) {
    inspector.allClasses().forEach(ServiceLoaderTestBase::verifyNoClassLoaders);
  }

  public static void verifyNoClassLoaders(ClassSubject classSubject) {
    assertTrue(classSubject.isPresent());
    classSubject.forAllMethods(
        method -> assertThat(method, not(invokesMethodWithName("getClassLoader"))));
  }

  public static void verifyNoServiceLoaderLoads(ClassSubject classSubject) {
    assertTrue(classSubject.isPresent());
    classSubject.forAllMethods(method -> assertThat(method, not(invokesMethodWithName("load"))));
  }

  public Map<String, List<String>> getServiceMappings() {
    return dataResourceConsumer.getAll().entrySet().stream()
        .filter(e -> e.getKey().startsWith(AppServices.SERVICE_DIRECTORY_NAME))
        .collect(
            Collectors.toMap(
                e -> e.getKey().substring(AppServices.SERVICE_DIRECTORY_NAME.length()),
                e -> e.getValue()));
  }

  public void verifyServiceMetaInf(
      CodeInspector inspector, Class<?> serviceClass, Class<?> serviceImplClass) {
    // Account for renaming, and for the impl to be merged with the interface.
    String finalServiceName = inspector.clazz(serviceClass).getFinalName();
    String finalImplName = inspector.clazz(serviceImplClass).getFinalName();
    if (finalServiceName == null) {
      finalServiceName = finalImplName;
    }
    Map<String, List<String>> actual = getServiceMappings();
    Map<String, List<String>> expected =
        ImmutableMap.of(finalServiceName, Collections.singletonList(finalImplName));
    assertEquals(expected, actual);
  }

  public void verifyServiceMetaInf(
      CodeInspector inspector,
      Class<?> serviceClass,
      Class<?> serviceImplClass1,
      Class<?> serviceImplClass2) {
    // Account for renaming. No class merging should happen.
    String finalServiceName = inspector.clazz(serviceClass).getFinalName();
    String finalImplName1 = inspector.clazz(serviceImplClass1).getFinalName();
    String finalImplName2 = inspector.clazz(serviceImplClass2).getFinalName();
    Map<String, List<String>> actual = getServiceMappings();
    Map<String, List<String>> expected =
        ImmutableMap.of(finalServiceName, Arrays.asList(finalImplName1, finalImplName2));
    assertEquals(expected, actual);
  }

  protected R8FullTestBuilder serviceLoaderTest(Class<?> serviceClass, Class<?>... implClasses)
      throws IOException {
    return serviceLoaderTestNoClasses(serviceClass, implClasses).addInnerClasses(getClass());
  }

  protected R8FullTestBuilder serviceLoaderTestNoClasses(
      Class<?> serviceClass, Class<?>... implClasses) throws IOException {
    R8FullTestBuilder ret =
        testForR8(parameters.getBackend())
            .setMinApi(parameters)
            .addOptionsModification(
                o -> {
                  dataResourceConsumer = new DataResourceConsumerForTesting(o.dataResourceConsumer);
                  o.dataResourceConsumer = dataResourceConsumer;
                  o.enableServiceLoaderRewriting = enableRewriting;
                })
            // Enables ServiceLoader optimization failure diagnostics.
            .enableExperimentalWhyAreYouNotInlining()
            .addKeepRules("-whyareyounotinlining class java.util.ServiceLoader { *** load(...); }");
    if (implClasses.length > 0) {
      String implLines =
          Arrays.stream(implClasses)
              .map(c -> c.getTypeName() + "\n")
              .collect(Collectors.joining(""));
      ret.addDataEntryResources(
          DataEntryResource.fromBytes(
              implLines.getBytes(),
              AppServices.SERVICE_DIRECTORY_NAME + serviceClass.getTypeName(),
              Origin.unknown()));
    }
    return ret;
  }
}
