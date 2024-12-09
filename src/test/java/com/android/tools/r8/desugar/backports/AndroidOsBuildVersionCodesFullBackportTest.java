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
import com.android.tools.r8.transformers.ClassFileTransformer;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.lang.reflect.Field;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AndroidOsBuildVersionCodesFullBackportTest extends AbstractBackportTest {

  private static final String ANDROID_OS_BUILD_VERSION_CODES_FULL_TYPE_NAME =
      "android.os.Build$VERSION_CODES_FULL";
  private static final String ANDROID_OS_BUILD_VERSION_CODES_FULL_DESCRIPTOR =
      DescriptorUtils.javaTypeToDescriptor(ANDROID_OS_BUILD_VERSION_CODES_FULL_TYPE_NAME);

  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return getTestParameters()
        .withDexRuntimesStartingFromExcluding(Version.V4_0_4)
        .withAllApiLevels()
        .build();
  }

  public AndroidOsBuildVersionCodesFullBackportTest(TestParameters parameters) throws IOException {
    super(
        parameters,
        ANDROID_OS_BUILD_VERSION_CODES_FULL_TYPE_NAME,
        ImmutableList.of(getTestRunner()));

    // android.os.Build$VERSION.SDK_INT_FULL is on API 36.
    registerFieldTarget(AndroidApiLevel.BAKLAVA, 36);
  }

  @Override
  protected void configure(D8TestBuilder builder) throws Exception {
    // Use BAKLAVA library to get API level for getMajorVersion() and getMinorVersion().
    builder.addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.BAKLAVA));
  }

  @Override
  // Add android.os.Build$VERSION_CODES_FULL class to runtime classpath.
  protected void configure(D8TestCompileResult result) throws Exception {
    // Only add this for BAKLAVA or higher where this is not backported away.
    if (parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.BAKLAVA)) {
      result.addRunClasspathFiles(
          testForD8()
              .addProgramClassFileData(
                  getTransformedBuildVERSION_CODES_FULLClassForRuntimeClasspath())
              .setMinApi(parameters.getApiLevel())
              .compile()
              .writeToZip());
    }
  }

  private static byte[] getTransformedBuildVERSION_CODES_FULLClassForRuntimeClasspath()
      throws IOException {
    ClassFileTransformer transformer =
        transformer(VERSION_CODES_FULL.class)
            .setClassDescriptor(ANDROID_OS_BUILD_VERSION_CODES_FULL_DESCRIPTOR);
    for (Field field : VERSION_CODES_FULL.class.getDeclaredFields()) {
      transformer.setAccessFlags(field, AccessFlags::setFinal);
    }
    return transformer.transform();
  }

  private static byte[] getTestRunner() throws IOException {
    return transformer(TestRunner.class)
        .replaceClassDescriptorInMethodInstructions(
            descriptor(VERSION_CODES_FULL.class), ANDROID_OS_BUILD_VERSION_CODES_FULL_DESCRIPTOR)
        .transform();
  }

  // android.os.Build$VERSION_CODES_FULL for building TestRunner and for runtime classpath.
  // Fields are not final to insure that the static gets are not inlined by javac for the test code.
  public static class /*android.os.Build$*/ VERSION_CODES_FULL {

    public static /* final */ int BASE = 10_000;
    public static /* final */ int BASE_1_1 = 20_000;
    public static /* final */ int CUPCAKE = 30_000;
    public static /* final */ int DONUT = 40_000;
    public static /* final */ int ECLAIR = 50_000;
    public static /* final */ int ECLAIR_0_1 = 60_000;
    public static /* final */ int ECLAIR_MR1 = 70_000;
    public static /* final */ int FROYO = 80_000;
    public static /* final */ int GINGERBREAD = 90_000;
    public static /* final */ int GINGERBREAD_MR1 = 100_000;
    public static /* final */ int HONEYCOMB = 110_000;
    public static /* final */ int HONEYCOMB_MR1 = 120_000;
    public static /* final */ int HONEYCOMB_MR2 = 130_000;
    public static /* final */ int ICE_CREAM_SANDWICH = 140_000;
    public static /* final */ int ICE_CREAM_SANDWICH_MR1 = 150_000;
    public static /* final */ int JELLY_BEAN = 160_000;
    public static /* final */ int JELLY_BEAN_MR1 = 170_000;
    public static /* final */ int JELLY_BEAN_MR2 = 180_000;
    public static /* final */ int KITKAT = 190_000;
    public static /* final */ int KITKAT_WATCH = 200_000;
    public static /* final */ int LOLLIPOP = 210_000;
    public static /* final */ int LOLLIPOP_MR1 = 220_000;
    public static /* final */ int M = 230_000;
    public static /* final */ int N = 240_000;
    public static /* final */ int N_MR1 = 250_000;
    public static /* final */ int O = 260_000;
    public static /* final */ int O_MR1 = 270_000;
    public static /* final */ int P = 280_000;
    public static /* final */ int Q = 290_000;
    public static /* final */ int R = 300_000;
    public static /* final */ int S = 310_000;
    public static /* final */ int S_V2 = 320_000;
    public static /* final */ int TIRAMISU = 330_000;
    public static /* final */ int UPSIDE_DOWN_CAKE = 340_000;
    public static /* final */ int VANILLA_ICE_CREAM = 350_000;
  }

  public static class TestRunner extends MiniAssert {

    public static void main(String[] args) throws Exception {
      assertEquals(10_000, VERSION_CODES_FULL.BASE);
      assertEquals(20_000, VERSION_CODES_FULL.BASE_1_1);
      assertEquals(30_000, VERSION_CODES_FULL.CUPCAKE);
      assertEquals(40_000, VERSION_CODES_FULL.DONUT);
      assertEquals(50_000, VERSION_CODES_FULL.ECLAIR);
      assertEquals(60_000, VERSION_CODES_FULL.ECLAIR_0_1);
      assertEquals(70_000, VERSION_CODES_FULL.ECLAIR_MR1);
      assertEquals(80_000, VERSION_CODES_FULL.FROYO);
      assertEquals(90_000, VERSION_CODES_FULL.GINGERBREAD);
      assertEquals(100_000, VERSION_CODES_FULL.GINGERBREAD_MR1);
      assertEquals(110_000, VERSION_CODES_FULL.HONEYCOMB);
      assertEquals(120_000, VERSION_CODES_FULL.HONEYCOMB_MR1);
      assertEquals(130_000, VERSION_CODES_FULL.HONEYCOMB_MR2);
      assertEquals(140_000, VERSION_CODES_FULL.ICE_CREAM_SANDWICH);
      assertEquals(150_000, VERSION_CODES_FULL.ICE_CREAM_SANDWICH_MR1);
      assertEquals(160_000, VERSION_CODES_FULL.JELLY_BEAN);
      assertEquals(170_000, VERSION_CODES_FULL.JELLY_BEAN_MR1);
      assertEquals(180_000, VERSION_CODES_FULL.JELLY_BEAN_MR2);
      assertEquals(190_000, VERSION_CODES_FULL.KITKAT);
      assertEquals(200_000, VERSION_CODES_FULL.KITKAT_WATCH);
      assertEquals(210_000, VERSION_CODES_FULL.LOLLIPOP);
      assertEquals(220_000, VERSION_CODES_FULL.LOLLIPOP_MR1);
      assertEquals(230_000, VERSION_CODES_FULL.M);
      assertEquals(240_000, VERSION_CODES_FULL.N);
      assertEquals(250_000, VERSION_CODES_FULL.N_MR1);
      assertEquals(260_000, VERSION_CODES_FULL.O);
      assertEquals(270_000, VERSION_CODES_FULL.O_MR1);
      assertEquals(280_000, VERSION_CODES_FULL.P);
      assertEquals(290_000, VERSION_CODES_FULL.Q);
      assertEquals(300_000, VERSION_CODES_FULL.R);
      assertEquals(310_000, VERSION_CODES_FULL.S);
      assertEquals(320_000, VERSION_CODES_FULL.S_V2);
      assertEquals(330_000, VERSION_CODES_FULL.TIRAMISU);
      assertEquals(340_000, VERSION_CODES_FULL.UPSIDE_DOWN_CAKE);
      assertEquals(350_000, VERSION_CODES_FULL.VANILLA_ICE_CREAM);
    }
  }
}
