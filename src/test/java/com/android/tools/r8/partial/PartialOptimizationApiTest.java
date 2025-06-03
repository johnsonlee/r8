// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import static com.android.tools.r8.MarkerMatcher.markerBackend;
import static com.android.tools.r8.MarkerMatcher.markerCompilationMode;
import static com.android.tools.r8.MarkerMatcher.markerMinApi;
import static com.android.tools.r8.MarkerMatcher.markerTool;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.PartialOptimizationConfigurationBuilder;
import com.android.tools.r8.PartialOptimizationConfigurationProvider;
import com.android.tools.r8.ProgramConsumer;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.compilerapi.CompilerApiTest;
import com.android.tools.r8.compilerapi.CompilerApiTestRunner;
import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.dex.Marker.Tool;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ThrowingTriConsumer;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Path;
import java.util.Collection;
import org.hamcrest.CoreMatchers;
import org.junit.Test;

public class PartialOptimizationApiTest extends CompilerApiTestRunner {

  public static final int MIN_API_LEVEL = 31;

  public PartialOptimizationApiTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<? extends CompilerApiTest> binaryTestClass() {
    return ApiTest.class;
  }

  @Test
  public void testR8() throws Exception {
    ApiTest test = new ApiTest(ApiTest.PARAMETERS);
    runTest(test::runR8);
  }

  private void runTest(
      ThrowingTriConsumer<
              ProgramConsumer,
              PartialOptimizationConfigurationProvider,
              PartialOptimizationConfigurationProvider,
              Exception>
          test)
      throws Exception {
    Path output = temp.newFolder().toPath().resolve("out.jar");
    ProviderUsingClass providerUsingClass = new ProviderUsingClass();
    ProviderUsingPackage providerUsingPackage = new ProviderUsingPackage();
    test.accept(
        new DexIndexedConsumer.ArchiveConsumer(output), providerUsingClass, providerUsingPackage);

    assertTrue(providerUsingClass.called);
    assertTrue(providerUsingPackage.called);
    Collection<Marker> markers = new CodeInspector(output).getMarkers();
    assertEquals(1, markers.size());
    assertThat(
        markers,
        CoreMatchers.everyItem(
            CoreMatchers.allOf(
                markerBackend(Backend.DEX),
                markerCompilationMode(CompilationMode.RELEASE),
                markerMinApi(AndroidApiLevel.getAndroidApiLevel(MIN_API_LEVEL)),
                markerTool(Tool.R8Partial))));
  }

  public static class ApiTest extends CompilerApiTest {

    public ApiTest(Object parameters) {
      super(parameters);
    }

    public void runR8(
        ProgramConsumer programConsumer,
        PartialOptimizationConfigurationProvider provider1,
        PartialOptimizationConfigurationProvider provider2)
        throws Exception {
      R8.run(
          R8Command.builder()
              .addClassProgramData(getBytesForClass(getMockClass()), Origin.unknown())
              .addProguardConfiguration(getKeepMainRules(getMockClass()), Origin.unknown())
              .addLibraryFiles(getJava8RuntimeJar())
              .setProgramConsumer(programConsumer)
              .addPartialOptimizationConfigurationProviders(provider1, provider2)
              .setMinApiLevel(MIN_API_LEVEL)
              .build());
    }

    @Test
    public void testR8() throws Exception {
      runR8(
          DexIndexedConsumer.emptyConsumer(), new ProviderUsingClass(), new ProviderUsingPackage());
    }
  }

  public static class ProviderUsingClass implements PartialOptimizationConfigurationProvider {
    private boolean called = false;

    @Override
    public void getPartialOptimizationConfiguration(
        PartialOptimizationConfigurationBuilder builder) {
      called = true;
      builder.addClass(
          Reference.classFromTypeName("com.android.tools.r8.compilerapi.mockdata.MockClass"));
    }
  }

  public static class ProviderUsingPackage implements PartialOptimizationConfigurationProvider {
    private boolean called = false;

    @Override
    public void getPartialOptimizationConfiguration(
        PartialOptimizationConfigurationBuilder builder) {
      called = true;
      builder.addPackage(
          Reference.packageFromString("com.android.tools.r8.compilerapi.mockpackage"));
    }
  }
}
