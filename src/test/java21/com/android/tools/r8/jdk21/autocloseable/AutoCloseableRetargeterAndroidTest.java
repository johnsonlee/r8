// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk21.autocloseable;

import static com.android.tools.r8.desugar.AutoCloseableAndroidLibraryFileData.compileAutoCloseableAndroidLibraryClasses;
import static com.android.tools.r8.desugar.AutoCloseableAndroidLibraryFileData.getAutoCloseableAndroidClassData;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.TestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.AutoCloseableAndroidLibraryFileData.ContentProviderClient;
import com.android.tools.r8.desugar.AutoCloseableAndroidLibraryFileData.DrmManagerClient;
import com.android.tools.r8.desugar.AutoCloseableAndroidLibraryFileData.MediaDrm;
import com.android.tools.r8.desugar.AutoCloseableAndroidLibraryFileData.MediaMetadataRetriever;
import com.android.tools.r8.desugar.AutoCloseableAndroidLibraryFileData.TypedArray;
import com.android.tools.r8.desugar.backports.AbstractBackportTest;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AutoCloseableRetargeterAndroidTest extends AbstractBackportTest {

  enum CompileTimeLib {
    NONE,
    DEFAULT,
    FULL
  }

  private final CompileTimeLib compileTimeLib;

  @Parameters(name = "{0}, lib: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withDexRuntimes()
            .withCfRuntimesStartingFromIncluding(CfVm.JDK21)
            .withApiLevelsStartingAtIncluding(AndroidApiLevel.K)
            .enableApiLevelsForCf()
            .build(),
        CompileTimeLib.values());
  }

  public AutoCloseableRetargeterAndroidTest(
      TestParameters parameters, CompileTimeLib compileTimeLib) throws IOException {
    super(parameters, getTestRunner(), ImmutableList.of(getTestRunner()));
    this.compileTimeLib = compileTimeLib;
    // The constructor is used by the test and release has been available since API 5 and is the
    // method close is rewritten to.
    ignoreInvokes("<init>");
    ignoreInvokes("release");

    registerTarget(AndroidApiLevel.B, 5);
  }

  protected void configureProgram(TestBuilder<?, ?> builder) throws Exception {
    super.configureProgram(builder);
    if (builder.isJvmTestBuilder()) {
      builder.addProgramClassFileData(getAutoCloseableAndroidClassData(parameters));
    } else {
      if (compileTimeLib == CompileTimeLib.FULL) {
        builder
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.BAKLAVA))
            .addLibraryClassFileData(getAutoCloseableAndroidClassData(parameters));
      } else if (compileTimeLib == CompileTimeLib.NONE) {
        if (builder.isD8TestBuilder()) {
          builder.asD8TestBuilder().setUseDefaultRuntimeLibrary(false);
        } else {
          assert builder.isJvmTestBuilder();
          // No need to run without default runtime library on the JVM.
          Assume.assumeFalse(builder.isJvmTestBuilder());
        }
      }
    }
  }

  @Override
  protected void configure(D8TestCompileResult builder) throws Exception {
    if (parameters.isDexRuntime()) {
      builder.addBootClasspathFiles(compileAutoCloseableAndroidLibraryClasses(this, parameters));
    } else {
      builder.addRunClasspathClassFileData(getAutoCloseableAndroidClassData(parameters));
    }
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addStrippedOuter(getClass())
        .apply(this::configureProgram)
        .run(parameters.getRuntime(), getTestClassName())
        // Fails when not desugared.
        .assertFailureWithErrorThatMatches(containsString("close should not be called"));
  }

  private static byte[] getTestRunner() throws IOException {
    return transformer(TestRunner.class)
        .replaceClassDescriptorInMethodInstructions(
            descriptor(ContentProviderClient.class),
            DexItemFactory.androidContentContentProviderClientDescriptorString)
        .replaceClassDescriptorInMethodInstructions(
            descriptor(DrmManagerClient.class),
            DexItemFactory.androidDrmDrmManagerClientDescriptorString)
        .replaceClassDescriptorInMethodInstructions(
            descriptor(MediaDrm.class), DexItemFactory.androidMediaMediaDrmDescriptorString)
        .replaceClassDescriptorInMethodInstructions(
            descriptor(MediaMetadataRetriever.class),
            DexItemFactory.androidMediaMediaMetadataRetrieverDescriptorString)
        .replaceClassDescriptorInMethodInstructions(
            descriptor(TypedArray.class),
            DexItemFactory.androidContentResTypedArrayDescriptorString)
        .clearNest()
        .transform();
  }

  public static class TestRunner extends MiniAssert {

    public static void main(String[] args) {
      contentProviderClient();
      drmManagerClient();
      mediaDrm();
      mediaMetadataRetriever();
      typedArray();
    }

    private static void contentProviderClient() {
      ContentProviderClient contentProviderClient = new ContentProviderClient();
      MiniAssert.assertFalse(contentProviderClient.wasClosed);
      // Loop as regression test for b/276874854.
      for (int i = 0; i < 2; i++) {
        contentProviderClient.close();
        MiniAssert.assertTrue(contentProviderClient.wasClosed);
      }
    }

    public static void drmManagerClient() {
      DrmManagerClient drmManagerClient = new DrmManagerClient();
      MiniAssert.assertFalse(drmManagerClient.wasClosed);
      drmManagerClient.close();
      MiniAssert.assertTrue(drmManagerClient.wasClosed);
    }

    public static void mediaDrm() {
      MediaDrm mediaDrm = new MediaDrm();
      MiniAssert.assertFalse(mediaDrm.wasClosed);
      mediaDrm.close();
      MiniAssert.assertTrue(mediaDrm.wasClosed);
    }

    public static void mediaMetadataRetriever() {
      MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
      MiniAssert.assertFalse(mediaMetadataRetriever.wasClosed);
      mediaMetadataRetriever.close();
      MiniAssert.assertTrue(mediaMetadataRetriever.wasClosed);
    }

    public static void typedArray() {
      TypedArray typedArray = new TypedArray();
      MiniAssert.assertFalse(typedArray.wasClosed);
      typedArray.close();
      MiniAssert.assertTrue(typedArray.wasClosed);
    }
  }
}
