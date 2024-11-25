// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.backports;

import com.android.tools.r8.TestParameters;
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

  public AndroidOsBuildVersionBackportTest(TestParameters parameters)
      throws IOException, NoSuchFieldException {
    super(
        parameters,
        ANDROID_OS_BUILD_VERSION_TYPE_NAME,
        ImmutableList.of(getTestRunner(), getTransformedBuildVERSIONClass()));

    // android.os.Build$VERSION.SDK_INT_FULL is on API 36.
    registerFieldTarget(AndroidApiLevel.BAKLAVA, 1);
  }

  // Stub out android.os.Build$VERSION as it does not exist when building R8.
  public static class /*android.os.Build$*/ VERSION {

    public static /*final*/ int SDK_INT = -1;
    public static /*final*/ int SDK_INT_FULL = -1;
  }

  private static byte[] getTransformedBuildVERSIONClass() throws IOException, NoSuchFieldException {
    return transformer(VERSION.class)
        .setClassDescriptor(ANDROID_OS_BUILD_VERSION_DESCRIPTOR)
        .setAccessFlags(VERSION.class.getDeclaredField("SDK_INT"), AccessFlags::setFinal)
        .setAccessFlags(VERSION.class.getDeclaredField("SDK_INT_FULL"), AccessFlags::setFinal)
        .transform();
  }

  private static byte[] getTestRunner() throws IOException {
    return transformer(TestRunner.class)
        .replaceClassDescriptorInMethodInstructions(
            descriptor(VERSION.class), ANDROID_OS_BUILD_VERSION_DESCRIPTOR)
        .transform();
  }

  public static class TestRunner extends MiniAssert {

    public static void main(String[] args) throws Exception {
      System.out.println(VERSION.SDK_INT_FULL);
    }
  }
}
