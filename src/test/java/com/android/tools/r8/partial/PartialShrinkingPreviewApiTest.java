// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import static com.android.tools.r8.MarkerMatcher.markerBackend;
import static com.android.tools.r8.MarkerMatcher.markerCompilationMode;
import static com.android.tools.r8.MarkerMatcher.markerMinApi;
import static com.android.tools.r8.MarkerMatcher.markerTool;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.ProgramConsumer;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.compilerapi.CompilerApiTest;
import com.android.tools.r8.compilerapi.CompilerApiTestRunner;
import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.dex.Marker.Tool;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Path;
import java.util.Collection;
import org.hamcrest.CoreMatchers;
import org.junit.Test;

public class PartialShrinkingPreviewApiTest extends CompilerApiTestRunner {

  public static final int MIN_API_LEVEL = 31;

  public PartialShrinkingPreviewApiTest(TestParameters parameters) {
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

  private void runTest(ThrowingConsumer<ProgramConsumer, Exception> test) throws Exception {
    Path output = temp.newFolder().toPath().resolve("out.jar");
    test.accept(new DexIndexedConsumer.ArchiveConsumer(output));

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

    public void runR8(ProgramConsumer programConsumer) throws Exception {
      R8.run(
          R8Command.builder()
              .addClassProgramData(getBytesForClass(getMockClass()), Origin.unknown())
              .addProguardConfiguration(getKeepMainRules(getMockClass()), Origin.unknown())
              .addLibraryFiles(getJava8RuntimeJar())
              .setProgramConsumer(programConsumer)
              .enableExperimentalPartialShrinking("**", null)
              .setMinApiLevel(MIN_API_LEVEL)
              .build());
    }

    @Test
    public void testR8() throws Exception {
      runR8(DexIndexedConsumer.emptyConsumer());
    }
  }
}
