// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package autocloseable;

import static com.android.tools.r8.desugar.AutoCloseableAndroidLibraryFileData.compileAutoCloseableAndroidLibraryClasses;
import static com.android.tools.r8.desugar.AutoCloseableAndroidLibraryFileData.getAutoCloseableAndroidClassData;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.TestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.AutoCloseableAndroidLibraryFileData.ContentProviderClientApiLevel24;
import com.android.tools.r8.desugar.AutoCloseableAndroidLibraryFileData.DrmManagerClientApiLevel24;
import com.android.tools.r8.desugar.AutoCloseableAndroidLibraryFileData.MediaDrmApiLevel28;
import com.android.tools.r8.desugar.AutoCloseableAndroidLibraryFileData.MediaMetadataRetrieverApiLevel29;
import com.android.tools.r8.desugar.AutoCloseableAndroidLibraryFileData.TypedArrayAndroidApiLevel31;
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
public class AutoCloseableRetargeterAndroidSubtypeTwrTest extends AbstractBackportTest {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntime(CfVm.JDK21)
        .withDexRuntimes()
        .withApiLevelsStartingAtIncluding(AndroidApiLevel.K)
        .enableApiLevelsForCf()
        .build();
  }

  public AutoCloseableRetargeterAndroidSubtypeTwrTest(TestParameters parameters)
      throws IOException {
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

    registerTarget(AndroidApiLevel.B, 5);
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
            descriptor(ContentProviderClientApiLevel24.class),
            DexItemFactory.androidContentContentProviderClientDescriptorString)
        .replaceClassDescriptorInMethodInstructions(
            descriptor(DrmManagerClientApiLevel24.class),
            DexItemFactory.androidDrmDrmManagerClientDescriptorString)
        .replaceClassDescriptorInMethodInstructions(
            descriptor(MediaMetadataRetrieverApiLevel29.class),
            DexItemFactory.androidMediaMediaMetadataRetrieverDescriptorString)
        .replaceClassDescriptorInMethodInstructions(
            descriptor(TypedArrayAndroidApiLevel31.class),
            DexItemFactory.androidContentResTypedArrayDescriptorString)
        .replaceClassDescriptorInMethodInstructions(
            descriptor(MediaDrmApiLevel28.class),
            DexItemFactory.androidMediaMediaDrmDescriptorString)
        .clearNest()
        .transform();
  }

  public static class ContentProviderClientOverride extends ContentProviderClientApiLevel24 {

    public void close() {
      super.close();
      System.out.println("close content provider");
    }
  }

  public static class ContentProviderClientSub extends ContentProviderClientApiLevel24 {}

  public static class DrmManagerClientOverride extends DrmManagerClientApiLevel24 {

    public void close() {
      super.close();
      System.out.println("close drm manager");
    }
  }

  public static class DrmManagerClientSub extends DrmManagerClientApiLevel24 {}

  public static class MediaMetadataRetrieverOverride extends MediaMetadataRetrieverApiLevel29 {

    public void close() {
      super.close();
      System.out.println("close media metadata");
    }
  }

  public static class MediaMetadataRetrieverSub extends MediaMetadataRetrieverApiLevel29 {}

  public static class TypedArrayOverride extends TypedArrayAndroidApiLevel31 {

    public void close() {
      super.close();
      System.out.println("close typed array");
    }
  }

  public static class TypedArraySub extends TypedArrayAndroidApiLevel31 {}

  public static class TestRunner extends MiniAssert {

    public static void main(String[] args) {
      raw();
      contentProviderClient();
      drmManagerClient();
      mediaMetadataRetriever();
      typedArray();
    }

    private static void raw() {
      ContentProviderClientApiLevel24[] box1 = new ContentProviderClientApiLevel24[1];
      try (ContentProviderClientApiLevel24 val = new ContentProviderClientApiLevel24()) {
        MiniAssert.assertFalse(val.wasClosed);
        box1[0] = val;
      }
      MiniAssert.assertTrue(box1[0].wasClosed);

      DrmManagerClientApiLevel24[] box2 = new DrmManagerClientApiLevel24[1];
      try (DrmManagerClientApiLevel24 val = new DrmManagerClientApiLevel24()) {
        MiniAssert.assertFalse(val.wasClosed);
        box2[0] = val;
      }
      MiniAssert.assertTrue(box2[0].wasClosed);

      MediaDrmApiLevel28[] box3 = new MediaDrmApiLevel28[1];
      try (MediaDrmApiLevel28 val = new MediaDrmApiLevel28()) {
        MiniAssert.assertFalse(val.wasClosed);
        box3[0] = val;
      }
      MiniAssert.assertTrue(box3[0].wasClosed);

      MediaMetadataRetrieverApiLevel29[] box4 = new MediaMetadataRetrieverApiLevel29[1];
      try (MediaMetadataRetrieverApiLevel29 val = new MediaMetadataRetrieverApiLevel29()) {
        MiniAssert.assertFalse(val.wasClosed);
        box4[0] = val;
      }
      MiniAssert.assertTrue(box4[0].wasClosed);

      TypedArrayAndroidApiLevel31[] box5 = new TypedArrayAndroidApiLevel31[1];
      try (TypedArrayAndroidApiLevel31 val = new TypedArrayAndroidApiLevel31()) {
        MiniAssert.assertFalse(val.wasClosed);
        box5[0] = val;
      }
      MiniAssert.assertTrue(box5[0].wasClosed);
    }

    private static void contentProviderClient() {
      ContentProviderClientSub[] box1 = new ContentProviderClientSub[1];
      try (ContentProviderClientSub val = new ContentProviderClientSub()) {
        MiniAssert.assertFalse(val.wasClosed);
        box1[0] = val;
      }
      MiniAssert.assertTrue(box1[0].wasClosed);

      ContentProviderClientOverride[] box2 = new ContentProviderClientOverride[1];
      try (ContentProviderClientOverride val = new ContentProviderClientOverride()) {
        MiniAssert.assertFalse(val.wasClosed);
        box2[0] = val;
      }
      MiniAssert.assertTrue(box2[0].wasClosed);
    }

    public static void drmManagerClient() {
      DrmManagerClientSub[] box1 = new DrmManagerClientSub[1];
      try (DrmManagerClientSub val = new DrmManagerClientSub()) {
        MiniAssert.assertFalse(val.wasClosed);
        box1[0] = val;
      }
      MiniAssert.assertTrue(box1[0].wasClosed);

      DrmManagerClientOverride[] box2 = new DrmManagerClientOverride[1];
      try (DrmManagerClientOverride val = new DrmManagerClientOverride()) {
        MiniAssert.assertFalse(val.wasClosed);
        box2[0] = val;
      }
      MiniAssert.assertTrue(box2[0].wasClosed);
    }

    public static void mediaMetadataRetriever() {
      MediaMetadataRetrieverSub[] box1 = new MediaMetadataRetrieverSub[1];
      try (MediaMetadataRetrieverSub val = new MediaMetadataRetrieverSub()) {
        MiniAssert.assertFalse(val.wasClosed);
        box1[0] = val;
      }
      MiniAssert.assertTrue(box1[0].wasClosed);

      MediaMetadataRetrieverOverride[] box2 = new MediaMetadataRetrieverOverride[1];
      try (MediaMetadataRetrieverOverride val = new MediaMetadataRetrieverOverride()) {
        MiniAssert.assertFalse(val.wasClosed);
        box2[0] = val;
      }
      MiniAssert.assertTrue(box2[0].wasClosed);
    }

    public static void typedArray() {
      TypedArraySub[] box1 = new TypedArraySub[1];
      try (TypedArraySub val = new TypedArraySub()) {
        MiniAssert.assertFalse(val.wasClosed);
        box1[0] = val;
      }
      MiniAssert.assertTrue(box1[0].wasClosed);

      TypedArrayOverride[] box2 = new TypedArrayOverride[1];
      try (TypedArrayOverride val = new TypedArrayOverride()) {
        MiniAssert.assertFalse(val.wasClosed);
        box2[0] = val;
      }
      MiniAssert.assertTrue(box2[0].wasClosed);
    }
  }
}
