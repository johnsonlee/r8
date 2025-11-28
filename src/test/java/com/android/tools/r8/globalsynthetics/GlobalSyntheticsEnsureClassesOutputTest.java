// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.globalsynthetics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.GlobalSyntheticsGenerator;
import com.android.tools.r8.GlobalSyntheticsGeneratorCommand;
import com.android.tools.r8.GlobalSyntheticsTestingConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GlobalSyntheticsEnsureClassesOutputTest extends TestBase {

  private final Backend backend;

  @Parameters(name = "{0}, backend:{1}")
  public static List<Object[]> data() {
    return buildParameters(getTestParameters().withNoneRuntime().build(), Backend.values());
  }

  public GlobalSyntheticsEnsureClassesOutputTest(TestParameters parameters, Backend backend) {
    parameters.assertNoneRuntime();
    this.backend = backend;
  }

  @Test
  public void testNumberOfClassesOnK() throws Exception {
    GlobalSyntheticsTestingConsumer globalsConsumer = new GlobalSyntheticsTestingConsumer();
    GlobalSyntheticsGenerator.run(
        GlobalSyntheticsGeneratorCommand.builder()
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.API_DATABASE_LEVEL))
            .setMinApiLevel(AndroidApiLevel.K.getLevel())
            .setGlobalSyntheticsConsumer(globalsConsumer)
            .setClassfileDesugaringOnly(backend.isCf())
            .build());
    assertTrue(globalsConsumer.isSingleGlobal());
    testForD8(backend)
        .apply(
            b ->
                b.getBuilder().addGlobalSyntheticsResourceProviders(globalsConsumer.getProviders()))
        .setMinApi(AndroidApiLevel.K)
        .compile()
        .inspect(
            inspector -> assertEquals(backend.isDex() ? 1148 : 5, inspector.allClasses().size()));
  }

  @Test
  public void testNumberOfClassesOnLatest() throws Exception {
    GlobalSyntheticsTestingConsumer globalsConsumer = new GlobalSyntheticsTestingConsumer();
    GlobalSyntheticsGenerator.run(
        GlobalSyntheticsGeneratorCommand.builder()
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.API_DATABASE_LEVEL))
            .setMinApiLevel(AndroidApiLevel.LATEST.getLevel())
            .setGlobalSyntheticsConsumer(globalsConsumer)
            .setClassfileDesugaringOnly(backend.isCf())
            .build());
    assertTrue(globalsConsumer.isSingleGlobal());
    Set<String> expectedInOutput = new HashSet<>();
    // The output contains a RecordTag type that is mapped back to the original java.lang.Record by
    // our codeinspector.
    expectedInOutput.add(DexItemFactory.recordDescriptorString);
    expectedInOutput.add(DexItemFactory.varHandleDescriptorString);
    expectedInOutput.add(DexItemFactory.methodHandlesLookupDescriptorString);
    expectedInOutput.add(DexItemFactory.lambdaMethodAnnotationDescriptor);
    testForD8(backend)
        .apply(
            b ->
                b.getBuilder().addGlobalSyntheticsResourceProviders(globalsConsumer.getProviders()))
        .setMinApi(AndroidApiLevel.LATEST)
        .compile()
        .inspect(
            inspector ->
                assertEquals(
                    expectedInOutput,
                    inspector.allClasses().stream()
                        .map(FoundClassSubject::getFinalDescriptor)
                        .collect(Collectors.toSet())));
  }

  @Test
  public void testClassFileListOutput() throws Exception {
    Set<String> generatedGlobalSynthetics = SetUtils.newConcurrentHashSet();
    GlobalSyntheticsTestingConsumer globalsConsumer = new GlobalSyntheticsTestingConsumer();
    runGlobalSyntheticsGenerator(
        GlobalSyntheticsGeneratorCommand.builder()
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.API_DATABASE_LEVEL))
            .setMinApiLevel(AndroidApiLevel.K.getLevel())
            .setGlobalSyntheticsConsumer(globalsConsumer)
            .setClassfileDesugaringOnly(backend.isCf())
            .build(),
        options ->
            options.testing.globalSyntheticCreatedCallback =
                programClass -> {
                  String descriptor = programClass.getClassReference().getDescriptor();
                  generatedGlobalSynthetics.add(
                      descriptor
                          .replace(
                              "Ljava/lang/invoke/VarHandle",
                              "Lcom/android/tools/r8/DesugarVarHandle")
                          .replace(
                              "Ljava/lang/invoke/MethodHandles$Lookup",
                              "Lcom/android/tools/r8/DesugarMethodHandlesLookup"));
                });
    assertTrue(globalsConsumer.isSingleGlobal());
    testForD8()
        .apply(
            b ->
                b.getBuilder().addGlobalSyntheticsResourceProviders(globalsConsumer.getProviders()))
        .setMinApi(AndroidApiLevel.K)
        .compile()
        .inspect(
            inspector -> {
              Set<String> readGlobalSynthetics =
                  inspector.allClasses().stream()
                      .map(FoundClassSubject::getFinalDescriptor)
                      .collect(Collectors.toSet());
              assertEquals(generatedGlobalSynthetics, readGlobalSynthetics);
            });
  }
}
