// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.backports;

import com.android.tools.r8.D8TestBuilder;
import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.graph.AccessFlags;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AndroidOsBuildVersionBackportTest extends AbstractBackportTest {

  private static final String ANDROID_OS_BUILD_VERSION_TYPE_NAME = "android.os.Build$VERSION";
  private static final String ANDROID_OS_BUILD_VERSION_DESCRIPTOR =
      DescriptorUtils.javaTypeToDescriptor(ANDROID_OS_BUILD_VERSION_TYPE_NAME);

  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return getTestParameters()
        .withDexRuntimesStartingFromExcluding(Version.V4_0_4)
        .withAllApiLevels()
        .build();
  }

  public AndroidOsBuildVersionBackportTest(TestParameters parameters) throws IOException {
    super(parameters, ANDROID_OS_BUILD_VERSION_TYPE_NAME, ImmutableList.of(getTestRunner()));

    // android.os.Build$VERSION.SDK_INT_FULL is on API 36.
    registerFieldTarget(AndroidApiLevel.BAKLAVA, 1);
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
            .addProgramClassFileData(getTransformedBuildVERSIONClassForRuntimeClasspath())
            .setMinApi(parameters.getApiLevel())
            .compile()
            .writeToZip());
  }

  @Override
  protected String[] configureD8RunArguments() {
    return new String[] {Integer.toString(parameters.getApiLevel().getLevel())};
  }

  private static byte[] getTransformedBuildVERSIONClassForRuntimeClasspath()
      throws IOException, NoSuchFieldException {
    return transformer(VERSION.class)
        .setClassDescriptor(ANDROID_OS_BUILD_VERSION_DESCRIPTOR)
        .setAccessFlags(VERSION.class.getDeclaredField("SDK_INT"), AccessFlags::setFinal)
        // Add the field SDK_INT_FULL at runtime. Even though it is backported and the field access
        // is outlined the host VM fails with NoSuchFieldError.
        .setAccessFlags(VERSION.class.getDeclaredField("SDK_INT_FULL"), AccessFlags::setFinal)
        .transform();
  }

  private static byte[] getTestRunner() throws IOException {
    return transformer(TestRunner.class)
        .replaceClassDescriptorInMethodInstructions(
            descriptor(VERSION.class), ANDROID_OS_BUILD_VERSION_DESCRIPTOR)
        .transform();
  }

  // Minimal android.os.Build$VERSION for building TestRunner and for runtime classpath.
  public static class /*android.os.Build$*/ VERSION {

    public static /*final*/ int SDK_INT = 21; // Fake API level 21 for test.
    public static /*final*/ int SDK_INT_FULL = -1;
  }

  public static class TestRunner extends MiniAssert {

    public static void main(String[] args) throws Exception {
      // No desugaring use the SDK_INT_FULL from the injected android.os.Build$VERSION class.
      assertEquals(Integer.parseInt(args[0]) < 36 ? 2100_000 : -1, VERSION.SDK_INT_FULL);
    }
  }
}
