// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package autocloseable;

import static com.android.tools.r8.desugar.AutoCloseableAndroidLibraryFileData.compileAutoCloseableAndroidLibraryClasses;
import static com.android.tools.r8.desugar.AutoCloseableAndroidLibraryFileData.getAutoCloseableAndroidClassData;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.D8TestBuilder;
import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.TestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AutoCloseableRetargeterAndroidTest extends AbstractBackportTest {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntimes()
        .withApiLevelsStartingAtIncluding(AndroidApiLevel.K)
        .enableApiLevelsForCf()
        .build();
  }

  public AutoCloseableRetargeterAndroidTest(TestParameters parameters) throws IOException {
    super(parameters, getTestRunner(), ImmutableList.of(getTestRunner()));

    // The constructor is used by the test and release has been available since API 5 and is the
    // method close is rewritten to.
    ignoreInvokes("<init>");
    ignoreInvokes("release");

    registerTarget(AndroidApiLevel.B, 5);
  }

  @Override
  protected void configureD8Options(D8TestBuilder d8TestBuilder) throws IOException {
    d8TestBuilder.addOptionsModification(opt -> opt.testing.enableAutoCloseableDesugaring = true);
  }

  @Override
  protected void configureProgram(TestBuilder<?, ?> builder) throws Exception {
    super.configureProgram(builder);
    if (builder.isJvmTestBuilder()) {
      builder.addProgramClassFileData(getAutoCloseableAndroidClassData(parameters));
    } else {
      builder
          .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.BAKLAVA))
          .addLibraryClassFileData(getAutoCloseableAndroidClassData(parameters));
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
