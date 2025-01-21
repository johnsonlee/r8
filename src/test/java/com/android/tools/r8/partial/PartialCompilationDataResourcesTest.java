// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.DataDirectoryResource;
import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.DataEntryResource.ByteDataEntryResource;
import com.android.tools.r8.DataResourceConsumer;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PartialCompilationDataResourcesTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntimes()
        .withApiLevelsStartingAtIncluding(AndroidApiLevel.L)
        .build();
  }

  static class DataResources implements DataResourceConsumer {
    int dataEntryResources = 0;
    int dataDirectoryResources = 0;
    public int finished = 0;

    @Override
    public void accept(DataDirectoryResource directory, DiagnosticsHandler diagnosticsHandler) {
      dataDirectoryResources++;
    }

    @Override
    public void accept(DataEntryResource file, DiagnosticsHandler diagnosticsHandler) {
      dataEntryResources++;
    }

    @Override
    public void finished(DiagnosticsHandler handler) {
      finished++;
    }
  }

  static class ProgramConsumer implements DexIndexedConsumer {
    public int dexFiles = 0;
    public int finished = 0;
    public DataResources dataResources = new DataResources();

    @Override
    public DataResourceConsumer getDataResourceConsumer() {
      return dataResources;
    }

    @Override
    public void finished(DiagnosticsHandler handler) {
      finished++;
    }

    @Override
    public void accept(
        int fileIndex, ByteDataView data, Set<String> descriptors, DiagnosticsHandler handler) {
      dexFiles++;
    }
  }

  @Test
  public void testProgramConsumer() throws Exception {
    ProgramConsumer programConsumer = new ProgramConsumer();
    testForR8Partial(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClasses(A.class, B.class, Main.class)
        .addDataResources(
            DataEntryResource.fromBytes(new byte[] {0}, "1", Origin.unknown()),
            DataEntryResource.fromBytes(new byte[] {1}, "2", Origin.unknown()),
            DataEntryResource.fromBytes(new byte[] {2}, "3", Origin.unknown()),
            DataDirectoryResource.fromName("A/", Origin.unknown()),
            DataDirectoryResource.fromName("B/", Origin.unknown()))
        .addKeepMainRule(Main.class)
        .addKeepRules("-keepdirectories")
        .setR8PartialConfiguration(builder -> builder.includeAll().excludeClasses(A.class))
        .setProgramConsumer(programConsumer)
        .compile();
    assertEquals(1, programConsumer.dexFiles);
    assertEquals(1, programConsumer.finished);
    assertEquals(2, programConsumer.dataResources.dataDirectoryResources);
    assertEquals(3, programConsumer.dataResources.dataEntryResources);
    assertEquals(1, programConsumer.dataResources.finished);
  }

  @Test
  public void test() throws Exception {
    testForR8Partial(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClasses(A.class, B.class, Main.class)
        .addDataResources(
            new ByteDataEntryResource(
                "Hello, world!".getBytes(StandardCharsets.UTF_8),
                "data_resource.txt",
                Origin.unknown()))
        .addKeepMainRule(Main.class)
        .setR8PartialConfiguration(builder -> builder.includeAll().excludeClasses(A.class))
        .compile()
        .run(parameters.getRuntime(), Main.class, getClass().getTypeName())
        .assertSuccessWithOutputLines("Hello, world!");
  }

  public static class A {}

  public static class B {}

  public static class Main {

    public static void main(String[] args) throws Exception {
      byte[] buffer = new byte[1024];
      int offset = 0;
      int read;
      try (InputStream is = Main.class.getClassLoader().getResourceAsStream("data_resource.txt")) {
        while ((read = is.read(buffer, offset, 1024 - offset)) != -1) {
          offset += read;
        }
      }
      System.out.println(new String(buffer, 0, offset, StandardCharsets.UTF_8));
    }
  }
}
