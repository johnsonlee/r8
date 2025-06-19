// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResource;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResourceBuilder;
import com.android.tools.r8.dump.CompilerDump;
import com.android.tools.r8.utils.DumpInputFlags;
import com.android.tools.r8.utils.ZipUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AndroidResourcesDumpTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection parameters() {
    return getTestParameters().withDefaultDexRuntime().withMinimumApiLevel().build();
  }

  public static AndroidTestResource getTestResources(TemporaryFolder temp) throws Exception {
    return new AndroidTestResourceBuilder()
        .withSimpleManifestAndAppNameString()
        .addStringValue("foo", "the foobar string")
        .addXmlWithStringReference("file.xml", "foo")
        .build(temp);
  }

  public static AndroidTestResource getFeatureTestResources(TemporaryFolder temp)
      throws IOException {
    return new AndroidTestResourceBuilder()
        .withSimpleManifestAndAppNameString()
        .addStringValue("feature_foo", "the feature string")
        .addXmlWithStringReference("feature.xml", "feature_foo")
        .build(temp);
  }

  @Test
  public void testR8() throws Exception {
    Path dump = temp.newFile("with_resources.zip").toPath();
    AndroidTestResource testResources = getTestResources(temp);
    TemporaryFolder featureSplitTemp = ToolHelper.getTemporaryFolderForTest();
    featureSplitTemp.create();
    AndroidTestResource featureTestResources = getFeatureTestResources(featureSplitTemp);
    try {
      testForR8(parameters.getBackend())
          .addProgramClasses(Main.class)
          .addKeepMainRule(Main.class)
          .addAndroidResources(testResources)
          .addFeatureSplitAndroidResources(featureTestResources, "thefeature")
          .addOptionsModification(
              options -> options.setDumpInputFlags(DumpInputFlags.dumpToFile(dump)))
          .setMinApi(parameters)
          .compile();
      fail("Expected to fail compilation");
    } catch (CompilationFailedException ignored) {
    }
    CompilerDump compilerDump = CompilerDump.fromArchive(dump, temp.newFolder().toPath());
    validateResourceEquality(
        testResources,
        ImmutableList.of("res/xml/file.xml", "AndroidManifest.xml", "resources.pb"),
        compilerDump.getAndroidResources());
    validateResourceEquality(
        featureTestResources,
        ImmutableList.of("res/xml/feature.xml", "AndroidManifest.xml", "resources.pb"),
        compilerDump.getAndroidResourcesForFeature(1));
  }

  private static void validateResourceEquality(
      AndroidTestResource testResources, List<String> fileEntries, Path dumpInput)
      throws IOException {
    int resourceFileCount = 0;
    try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(dumpInput))) {
      ZipEntry nextEntry = zipInputStream.getNextEntry();
      Path resourceZip = testResources.getResourceZip();
      while (nextEntry != null) {
        resourceFileCount++;
        String name = nextEntry.getName();
        assertTrue(fileEntries.contains(name));
        // We allow xml files to have been rewritten.
        if (!name.endsWith(".xml")) {
          byte[] original = ZipUtils.readSingleEntry(resourceZip, name);
          assertArrayEquals(original, ByteStreams.toByteArray(zipInputStream));
        }
        nextEntry = zipInputStream.getNextEntry();
      }
    }
    assertEquals(resourceFileCount, fileEntries.size());
  }

  static class Main {

    public static void main(String[] args) {}
  }
}
