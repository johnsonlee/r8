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
import com.android.tools.r8.desugar.AutoCloseableAndroidLibraryFileData.MediaMetadataRetriever;
import com.android.tools.r8.desugar.AutoCloseableAndroidLibraryFileData.TypedArray;
import com.android.tools.r8.desugar.backports.AbstractBackportTest;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AutoCloseableRetargeterAndroidSubtypeTest extends AbstractBackportTest {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntimes()
        .withApiLevelsStartingAtIncluding(AndroidApiLevel.K)
        .enableApiLevelsForCf()
        .build();
  }

  public AutoCloseableRetargeterAndroidSubtypeTest(TestParameters parameters) throws IOException {
    super(
        parameters,
        getTestRunner(),
        ImmutableList.of(
            getTestRunner(),
            getTransformedSubclass(
                ContentProviderClientOverride.class,
                DexItemFactory.androidContentContentProviderClientDescriptorString),
            getTransformedSubclass(
                ContentProviderClientSub.class,
                DexItemFactory.androidContentContentProviderClientDescriptorString),
            getTransformedSubclass(
                DrmManagerClientOverride.class,
                DexItemFactory.androidDrmDrmManagerClientDescriptorString),
            getTransformedSubclass(
                DrmManagerClientSub.class,
                DexItemFactory.androidDrmDrmManagerClientDescriptorString),
            getTransformedSubclass(
                MediaMetadataRetrieverOverride.class,
                DexItemFactory.androidMediaMediaMetadataRetrieverDescriptorString),
            getTransformedSubclass(
                MediaMetadataRetrieverSub.class,
                DexItemFactory.androidMediaMediaMetadataRetrieverDescriptorString),
            getTransformedSubclass(
                TypedArrayOverride.class,
                DexItemFactory.androidContentResTypedArrayDescriptorString),
            getTransformedSubclass(
                TypedArraySub.class, DexItemFactory.androidContentResTypedArrayDescriptorString)));

    // The constructor is used by the test and release has been available since API 5 and is the
    // method close is rewritten to.
    ignoreInvokes("<init>");
    ignoreInvokes("release");

    registerTarget(AndroidApiLevel.B, 4);
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

  private static byte[] getTransformedSubclass(Class<?> sub, String superDesc) throws IOException {
    return transformer(sub)
        .setSuper(superDesc)
        .transformMethodInsnInMethod(
            "<init>",
            (opcode, owner, name, descriptor, isInterface, visitor) -> {
              assert name.equals("<init>");
              visitor.visitMethodInsn(
                  opcode,
                  DescriptorUtils.descriptorToInternalName(superDesc),
                  name,
                  descriptor,
                  isInterface);
            })
        .transformMethodInsnInMethod(
            "close",
            (opcode, owner, name, descriptor, isInterface, visitor) -> {
              if (name.equals("close")) {
                visitor.visitMethodInsn(
                    opcode,
                    DescriptorUtils.descriptorToInternalName(superDesc),
                    name,
                    descriptor,
                    isInterface);
              } else {
                visitor.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
              }
            })
        .clearNest()
        .transform();
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
            descriptor(MediaMetadataRetriever.class),
            DexItemFactory.androidMediaMediaMetadataRetrieverDescriptorString)
        .replaceClassDescriptorInMethodInstructions(
            descriptor(TypedArray.class),
            DexItemFactory.androidContentResTypedArrayDescriptorString)
        .clearNest()
        .transform();
  }

  public static class ContentProviderClientOverride extends ContentProviderClient {

    public void close() {
      super.close();
      System.out.println("close content provider");
    }
  }

  public static class ContentProviderClientSub extends ContentProviderClient {}

  public static class DrmManagerClientOverride extends DrmManagerClient {

    public void close() {
      super.close();
      System.out.println("close drm manager");
    }
  }

  public static class DrmManagerClientSub extends DrmManagerClient {}

  public static class MediaMetadataRetrieverOverride extends MediaMetadataRetriever {

    public void close() {
      super.close();
      System.out.println("close media metadata");
    }
  }

  public static class MediaMetadataRetrieverSub extends MediaMetadataRetriever {}

  public static class TypedArrayOverride extends TypedArray {

    public void close() {
      super.close();
      System.out.println("close typed array");
    }
  }

  public static class TypedArraySub extends TypedArray {}

  public static class TestRunner extends MiniAssert {

    public static void main(String[] args) {
      contentProviderClient();
      drmManagerClient();
      mediaMetadataRetriever();
      typedArray();
    }

    private static void contentProviderClient() {
      ContentProviderClientSub cpcSub = new ContentProviderClientSub();
      MiniAssert.assertFalse(cpcSub.wasClosed);
      cpcSub.close();
      MiniAssert.assertTrue(cpcSub.wasClosed);

      ContentProviderClientOverride cpcOverride = new ContentProviderClientOverride();
      MiniAssert.assertFalse(cpcOverride.wasClosed);
      cpcOverride.close();
      MiniAssert.assertTrue(cpcOverride.wasClosed);
    }

    public static void drmManagerClient() {
      DrmManagerClientSub drmMC = new DrmManagerClientSub();
      MiniAssert.assertFalse(drmMC.wasClosed);
      drmMC.close();
      MiniAssert.assertTrue(drmMC.wasClosed);

      DrmManagerClientOverride drmMCOverride = new DrmManagerClientOverride();
      MiniAssert.assertFalse(drmMCOverride.wasClosed);
      drmMCOverride.close();
      MiniAssert.assertTrue(drmMCOverride.wasClosed);
    }

    public static void mediaMetadataRetriever() {
      MediaMetadataRetrieverSub mmrSub = new MediaMetadataRetrieverSub();
      MiniAssert.assertFalse(mmrSub.wasClosed);
      mmrSub.close();
      MiniAssert.assertTrue(mmrSub.wasClosed);

      MediaMetadataRetrieverOverride mmrOverride = new MediaMetadataRetrieverOverride();
      MiniAssert.assertFalse(mmrOverride.wasClosed);
      mmrOverride.close();
      MiniAssert.assertTrue(mmrOverride.wasClosed);
    }

    public static void typedArray() {
      TypedArraySub taSub = new TypedArraySub();
      MiniAssert.assertFalse(taSub.wasClosed);
      taSub.close();
      MiniAssert.assertTrue(taSub.wasClosed);

      TypedArrayOverride taOverride = new TypedArrayOverride();
      MiniAssert.assertFalse(taOverride.wasClosed);
      taOverride.close();
      MiniAssert.assertTrue(taOverride.wasClosed);
    }

    // Forwards to MiniAssert to avoid having to make it public.
    public static void doFail(String message) {
      MiniAssert.fail(message);
    }
  }
}
