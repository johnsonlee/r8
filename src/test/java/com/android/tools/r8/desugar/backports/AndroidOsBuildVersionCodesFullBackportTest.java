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
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.lang.reflect.Field;
import org.junit.Assert;
import org.junit.Test;
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

  @Test
  public void checkVersionCodesFullInAndroidJar() throws Exception {
    ClassSubject versionCodesFullClass =
        new CodeInspector(parameters.getDefaultAndroidJar())
            .clazz("android.os.Build$VERSION_CODES_FULL");
    Assert.assertFalse(versionCodesFullClass.isPresent());
    // Update test when fully testing Android Baklava.
    Assert.assertFalse(parameters.getApiLevel().equals(AndroidApiLevel.BAKLAVA));
    if (parameters.getApiLevel().equals(AndroidApiLevel.V)) {
      versionCodesFullClass =
          new CodeInspector(ToolHelper.getAndroidJar(AndroidApiLevel.BAKLAVA))
              .clazz("android.os.Build$VERSION_CODES_FULL");
      Assert.assertTrue(versionCodesFullClass.isPresent());
      // Update test when more version codes full are added.
      Assert.assertEquals(35, versionCodesFullClass.allFields().size());
      // Copied from BackportedMethodRewriter.
      Object[][] versionCodesFull = {
        {"BASE", 100_000},
        {"BASE_1_1", 200_000},
        {"CUPCAKE", 300_000},
        {"DONUT", 400_000},
        {"ECLAIR", 500_000},
        {"ECLAIR_0_1", 600_000},
        {"ECLAIR_MR1", 700_000},
        {"FROYO", 800_000},
        {"GINGERBREAD", 900_000},
        {"GINGERBREAD_MR1", 1000_000},
        {"HONEYCOMB", 1100_000},
        {"HONEYCOMB_MR1", 1200_000},
        {"HONEYCOMB_MR2", 1300_000},
        {"ICE_CREAM_SANDWICH", 1400_000},
        {"ICE_CREAM_SANDWICH_MR1", 1500_000},
        {"JELLY_BEAN", 1600_000},
        {"JELLY_BEAN_MR1", 1700_000},
        {"JELLY_BEAN_MR2", 1800_000},
        {"KITKAT", 1900_000},
        {"KITKAT_WATCH", 2000_000},
        {"LOLLIPOP", 2100_000},
        {"LOLLIPOP_MR1", 2200_000},
        {"M", 2300_000},
        {"N", 2400_000},
        {"N_MR1", 2500_000},
        {"O", 2600_000},
        {"O_MR1", 2700_000},
        {"P", 2800_000},
        {"Q", 2900_000},
        {"R", 3000_000},
        {"S", 3100_000},
        {"S_V2", 3200_000},
        {"TIRAMISU", 3300_000},
        {"UPSIDE_DOWN_CAKE", 3400_000},
        {"VANILLA_ICE_CREAM", 3500_000},
      };
      for (Object[] versionCodeFull : versionCodesFull) {
        Assert.assertEquals(
            ((Integer) versionCodeFull[1]).intValue(),
            versionCodesFullClass
                .field("int", (String) versionCodeFull[0])
                .getField()
                .getStaticValue()
                .asDexValueInt()
                .value);
      }
    }
  }

  // android.os.Build$VERSION_CODES_FULL for building TestRunner and for runtime classpath.
  // Fields are not final to ensure that the static gets are not inlined by javac for the test code
  // and the field values are not relevant, as desugaring will replace them with the proper
  // constants.
  public static class /*android.os.Build$*/ VERSION_CODES_FULL {

    public static /* final */ int BASE = -1;
    public static /* final */ int BASE_1_1 = -1;
    public static /* final */ int CUPCAKE = -1;
    public static /* final */ int DONUT = -1;
    public static /* final */ int ECLAIR = -1;
    public static /* final */ int ECLAIR_0_1 = -1;
    public static /* final */ int ECLAIR_MR1 = -1;
    public static /* final */ int FROYO = -1;
    public static /* final */ int GINGERBREAD = -1;
    public static /* final */ int GINGERBREAD_MR1 = -1;
    public static /* final */ int HONEYCOMB = -1;
    public static /* final */ int HONEYCOMB_MR1 = -1;
    public static /* final */ int HONEYCOMB_MR2 = -1;
    public static /* final */ int ICE_CREAM_SANDWICH = -1;
    public static /* final */ int ICE_CREAM_SANDWICH_MR1 = -1;
    public static /* final */ int JELLY_BEAN = -1;
    public static /* final */ int JELLY_BEAN_MR1 = -1;
    public static /* final */ int JELLY_BEAN_MR2 = -1;
    public static /* final */ int KITKAT = -1;
    public static /* final */ int KITKAT_WATCH = -1;
    public static /* final */ int LOLLIPOP = -1;
    public static /* final */ int LOLLIPOP_MR1 = -1;
    public static /* final */ int M = -1;
    public static /* final */ int N = -1;
    public static /* final */ int N_MR1 = -1;
    public static /* final */ int O = -1;
    public static /* final */ int O_MR1 = -1;
    public static /* final */ int P = -1;
    public static /* final */ int Q = -1;
    public static /* final */ int R = -1;
    public static /* final */ int S = -1;
    public static /* final */ int S_V2 = -1;
    public static /* final */ int TIRAMISU = -1;
    public static /* final */ int UPSIDE_DOWN_CAKE = -1;
    public static /* final */ int VANILLA_ICE_CREAM = -1;
  }

  public static class TestRunner extends MiniAssert {

    public static void main(String[] args) throws Exception {
      assertEquals(100_000, VERSION_CODES_FULL.BASE);
      assertEquals(200_000, VERSION_CODES_FULL.BASE_1_1);
      assertEquals(300_000, VERSION_CODES_FULL.CUPCAKE);
      assertEquals(400_000, VERSION_CODES_FULL.DONUT);
      assertEquals(500_000, VERSION_CODES_FULL.ECLAIR);
      assertEquals(600_000, VERSION_CODES_FULL.ECLAIR_0_1);
      assertEquals(700_000, VERSION_CODES_FULL.ECLAIR_MR1);
      assertEquals(800_000, VERSION_CODES_FULL.FROYO);
      assertEquals(900_000, VERSION_CODES_FULL.GINGERBREAD);
      assertEquals(1000_000, VERSION_CODES_FULL.GINGERBREAD_MR1);
      assertEquals(1100_000, VERSION_CODES_FULL.HONEYCOMB);
      assertEquals(1200_000, VERSION_CODES_FULL.HONEYCOMB_MR1);
      assertEquals(1300_000, VERSION_CODES_FULL.HONEYCOMB_MR2);
      assertEquals(1400_000, VERSION_CODES_FULL.ICE_CREAM_SANDWICH);
      assertEquals(1500_000, VERSION_CODES_FULL.ICE_CREAM_SANDWICH_MR1);
      assertEquals(1600_000, VERSION_CODES_FULL.JELLY_BEAN);
      assertEquals(1700_000, VERSION_CODES_FULL.JELLY_BEAN_MR1);
      assertEquals(1800_000, VERSION_CODES_FULL.JELLY_BEAN_MR2);
      assertEquals(1900_000, VERSION_CODES_FULL.KITKAT);
      assertEquals(2000_000, VERSION_CODES_FULL.KITKAT_WATCH);
      assertEquals(2100_000, VERSION_CODES_FULL.LOLLIPOP);
      assertEquals(2200_000, VERSION_CODES_FULL.LOLLIPOP_MR1);
      assertEquals(2300_000, VERSION_CODES_FULL.M);
      assertEquals(2400_000, VERSION_CODES_FULL.N);
      assertEquals(2500_000, VERSION_CODES_FULL.N_MR1);
      assertEquals(2600_000, VERSION_CODES_FULL.O);
      assertEquals(2700_000, VERSION_CODES_FULL.O_MR1);
      assertEquals(2800_000, VERSION_CODES_FULL.P);
      assertEquals(2900_000, VERSION_CODES_FULL.Q);
      assertEquals(3000_000, VERSION_CODES_FULL.R);
      assertEquals(3100_000, VERSION_CODES_FULL.S);
      assertEquals(3200_000, VERSION_CODES_FULL.S_V2);
      assertEquals(3300_000, VERSION_CODES_FULL.TIRAMISU);
      assertEquals(3400_000, VERSION_CODES_FULL.UPSIDE_DOWN_CAKE);
      assertEquals(3500_000, VERSION_CODES_FULL.VANILLA_ICE_CREAM);
    }
  }
}
