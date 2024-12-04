// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.backports;

import com.android.tools.r8.D8TestBuilder;
import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.backports.AndroidOsBuildVersionBackportTest.VERSION;
import com.android.tools.r8.graph.AccessFlags;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AndroidOsBuildBackportTest extends AbstractBackportTest {

  private static final String ANDROID_OS_BUILD_VERSION_TYPE_NAME = "android.os.Build$VERSION";
  private static final String ANDROID_OS_BUILD_VERSION_DESCRIPTOR =
      DescriptorUtils.javaTypeToDescriptor(ANDROID_OS_BUILD_VERSION_TYPE_NAME);
  private static final String ANDROID_OS_BUILD_TYPE_NAME = "android.os.Build";
  private static final String ANDROID_OS_BUILD_DESCRIPTOR =
      DescriptorUtils.javaTypeToDescriptor(ANDROID_OS_BUILD_TYPE_NAME);

  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return getTestParameters()
        .withDexRuntimesStartingFromExcluding(Version.V4_0_4)
        .withAllApiLevels()
        .build();
  }

  public AndroidOsBuildBackportTest(TestParameters parameters) throws IOException {
    super(parameters, ANDROID_OS_BUILD_TYPE_NAME, ImmutableList.of(getTestRunner()));

    // android.os.Build getMajorSdkVersion and getMinorSdkVersion added on API 36.
    registerTarget(AndroidApiLevel.BAKLAVA, 4);
  }

  @Override
  protected void configure(D8TestBuilder builder) throws Exception {
    // Use BAKLAVA library to get API level for getMajorVersion() and getMinorVersion().
    builder.addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.BAKLAVA));
  }

  @Override
  // Add minimal android.os.Build$VERSION class to runtime classpath.
  protected void configure(D8TestCompileResult result) throws Exception {
    result.addRunClasspathFiles(
        testForD8()
            .addProgramClassFileData(getTransformedBuildVERSIONClass())
            .setMinApi(parameters.getApiLevel())
            .compile()
            .writeToZip());
  }

  private static byte[] getTransformedBuildVERSIONClass() throws IOException, NoSuchFieldException {
    return transformer(VERSION.class)
        .setClassDescriptor(ANDROID_OS_BUILD_VERSION_DESCRIPTOR)
        .setAccessFlags(VERSION.class.getDeclaredField("SDK_INT"), AccessFlags::setFinal)
        .transform();
  }

  private static byte[] getTestRunner() throws IOException {
    return transformer(TestRunner.class)
        .replaceClassDescriptorInMethodInstructions(
            descriptor(Build.class), ANDROID_OS_BUILD_DESCRIPTOR)
        .transform();
  }

  // Minimal android.os.Build for building TestRunner.
  public static class /*android.os.*/ Build {

    public static int getMajorSdkVersion(int sdkIntFull) {
      throw new RuntimeException();
    }

    public static int getMinorSdkVersion(int sdkIntFull) {
      throw new RuntimeException();
    }
  }

  // Minimal android.os.Build$VERSION for runtime classpath.
  public static class /*android.os.Build$*/ VERSION {

    public static /*final*/ int SDK_INT = -1;
  }

  public static class TestRunner extends MiniAssert {

    public static void main(String[] args) throws Exception {
      assertEquals(1, Build.getMajorSdkVersion(100_000));
      assertEquals(0, Build.getMinorSdkVersion(100_000));
      assertEquals(35, Build.getMajorSdkVersion(3500_000));
      assertEquals(0, Build.getMinorSdkVersion(3500_000));
    }
  }
}
