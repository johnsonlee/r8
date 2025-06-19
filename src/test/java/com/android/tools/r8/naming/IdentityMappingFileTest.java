// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanBox;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IdentityMappingFileTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public IdentityMappingFileTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  private void checkIdentityMappingContent(String mapping) {
    assertThat(mapping, containsString("# compiler: R8"));
    assertThat(mapping, containsString("# compiler_version: "));
    assertThat(mapping, containsString("# min_api: 1"));
    assertThat(mapping, containsString("# compiler_hash: "));
    assertThat(mapping, containsString("# common_typos_disable"));
    assertThat(mapping, containsString("# {\"id\":\"com.android.tools.r8.mapping\",\"version\":"));
    assertThat(mapping, containsString("# pg_map_id: "));
    assertThat(mapping, containsString("# pg_map_hash: SHA-256 "));
    // Check the mapping is the identity, e.g., only comments and identity entries are defined.
    int numberOfIdentityMappings = 0;
    for (String line : StringUtils.splitLines(mapping)) {
      if (line.startsWith("#")) {
        continue;
      }
      String[] parts = line.split(" -> ");
      if (parts.length == 2 && line.endsWith(":")) {
        String left = parts[0];
        String right = parts[1];
        if (left.equals(right.substring(0, right.length() - 1))) {
          numberOfIdentityMappings++;
          continue;
        }
      }
      fail("Expected comment or identity, got: " + line);
    }
    // It may be ok to not actually include any identity mapping, but currently we do.
    assertTrue(numberOfIdentityMappings > 0);
  }

  @Test
  public void testTheTestBuilder() throws Exception {
    System.setProperty("com.android.tools.r8.enableMapIdInSourceFile", "0");
    try {
      String mapping =
          testForR8(Backend.DEX)
              .addProgramClassFileData(getMainWithoutLineTable())
              .setMinApi(AndroidApiLevel.B)
              .addKeepMainRule(Main.class)
              .compile()
              .getProguardMap();
      checkIdentityMappingContent(mapping);
    } finally {
      System.clearProperty("com.android.tools.r8.enableMapIdInSourceFile");
    }
  }

  @Test
  public void testFileOutput() throws Exception {
    System.setProperty("com.android.tools.r8.enableMapIdInSourceFile", "0");
    try {
      Path mappingPath = temp.newFolder().toPath().resolve("mapping.map");
      R8.run(
          R8Command.builder()
              .addClassProgramData(getMainWithoutLineTable(), Origin.unknown())
              .addProguardConfiguration(
                  ImmutableList.of(keepMainProguardConfiguration(Main.class)), Origin.unknown())
              .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
              .setProguardMapOutputPath(mappingPath)
              .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
              .build());
      assertTrue(Files.exists(mappingPath));
      checkIdentityMappingContent(FileUtils.readTextFile(mappingPath, StandardCharsets.UTF_8));
    } finally {
      System.clearProperty("com.android.tools.r8.enableMapIdInSourceFile");
    }
  }

  @Test
  public void testStringConsumer() throws Exception {
    System.setProperty("com.android.tools.r8.enableMapIdInSourceFile", "0");
    try {
      BooleanBox consumerWasCalled = new BooleanBox(false);
      StringBuilder mappingContent = new StringBuilder();
      R8.run(
          R8Command.builder()
              .addClassProgramData(getMainWithoutLineTable(), Origin.unknown())
              .addProguardConfiguration(
                  ImmutableList.of(keepMainProguardConfiguration(Main.class)), Origin.unknown())
              .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
              .setProguardMapConsumer(
                  new StringConsumer() {
                    @Override
                    public void accept(String string, DiagnosticsHandler handler) {
                      mappingContent.append(string);
                    }

                    @Override
                    public void finished(DiagnosticsHandler handler) {
                      consumerWasCalled.set(true);
                    }
                  })
              .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
              .build());
      assertTrue(consumerWasCalled.get());
      checkIdentityMappingContent(mappingContent.toString());
    } finally {
      System.clearProperty("com.android.tools.r8.enableMapIdInSourceFile");
    }
  }

  private byte[] getMainWithoutLineTable() throws Exception {
    return transformer(Main.class).removeLineNumberTable(MethodPredicate.all()).transform();
  }

  // Compiling this program with a keep main will result in an identity mapping for the residual
  // program. The (identity) mapping should still be created and emitted to the client.
  static class Main {

    public static void main(String[] args) {
      System.out.println("Hello world!");
    }
  }
}
