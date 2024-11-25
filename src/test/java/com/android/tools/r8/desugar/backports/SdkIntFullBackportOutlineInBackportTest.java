// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.backports;

import static com.android.tools.r8.utils.AndroidApiLevel.BAKLAVA;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.androidapi.AndroidApiLevelHashingDatabaseImpl;
import com.android.tools.r8.graph.AccessFlags;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

@RunWith(Parameterized.class)
public class SdkIntFullBackportOutlineInBackportTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void checkSdkIntFullApiLevel() {
    assumeTrue(parameters.isNoneRuntime());
    TestDiagnosticMessagesImpl diagnosticsHandler = new TestDiagnosticMessagesImpl();
    InternalOptions options = new InternalOptions();
    AndroidApiLevelHashingDatabaseImpl androidApiLevelDatabase =
        new AndroidApiLevelHashingDatabaseImpl(ImmutableList.of(), options, diagnosticsHandler);
    assertEquals(
        BAKLAVA.getLevel(),
        androidApiLevelDatabase
            .getFieldApiLevel(
                options.dexItemFactory().androidOsBuildVersionMembers.SDK_INT_FULL.asDexField())
            .getLevel());
  }

  @Test
  public void testD8() throws Exception {
    for (AndroidApiLevel apiLevel : AndroidApiLevel.values()) {
      if (apiLevel.isGreaterThanOrEqualTo(AndroidApiLevel.LATEST)) {
        continue;
      }
      testForD8()
          .addProgramClassFileData(getTransformedMainClass())
          .addLibraryClassFileData(getTransformedBuildVersionClass())
          .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.BAKLAVA))
          .setMinApi(apiLevel)
          .compile()
          .inspect(inspector -> inspectD8(inspector, apiLevel));
    }
  }

  @Test
  public void testR8() throws Exception {
    for (AndroidApiLevel apiLevel : AndroidApiLevel.values()) {
      // SDK_INT was introduced in D so don't bother testing before.
      if (apiLevel.isLessThanOrEqualTo(AndroidApiLevel.D)
          || apiLevel.isGreaterThanOrEqualTo(AndroidApiLevel.LATEST)) {
        continue;
      }
      System.out.println(apiLevel);
      testForR8(Backend.DEX)
          .addProgramClassFileData(getTransformedMainClass())
          .addKeepMainRule(TestClass.class)
          .addLibraryClassFileData(getTransformedBuildVersionClass())
          .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.BAKLAVA))
          .setMinApi(apiLevel)
          .addDontObfuscate()
          .compile()
          .inspect(inspector -> inspectR8(inspector, apiLevel));
    }
  }

  private void inspectD8(CodeInspector inspector, AndroidApiLevel apiLevel) {
    if (apiLevel.isGreaterThanOrEqualTo(BAKLAVA)) {
      // From BAKLAVA the SDK_INT_FULL get stays.
      assertEquals(
          1,
          countStaticGets(
              inspector.clazz(TestClass.class).mainMethod(),
              inspector.getFactory().androidOsBuildVersionMembers.SDK_INT_FULL));
    } else {
      // Before BAKLAVA the SDK_INT_FULL static get is backported and the SDK_INT_FULL static get
      // is outlined from the backport as well.
      ClassSubject backport =
          inspector.clazz(
              SyntheticItemsTestUtils.syntheticBackportWithForwardingClass(TestClass.class, 1));
      assertThat(backport, isPresent());
      assertEquals(
          2,
          countStaticGets(
              backport.uniqueMethod(),
              inspector.getFactory().androidOsBuildVersionMembers.SDK_INT));
      assertEquals(
          0,
          countStaticGets(
              backport.uniqueMethod(),
              inspector.getFactory().androidOsBuildVersionMembers.SDK_INT_FULL));
      ClassSubject apiOutline =
          inspector.clazz(SyntheticItemsTestUtils.syntheticApiOutlineClass(TestClass.class, 0));
      assertThat(apiOutline.uniqueMethod(), isPresent());
      assertEquals(
          1,
          countStaticGets(
              apiOutline.uniqueMethod(),
              inspector.getFactory().androidOsBuildVersionMembers.SDK_INT_FULL));
    }
  }

  private void inspectR8(CodeInspector inspector, AndroidApiLevel apiLevel) {
    if (apiLevel.isGreaterThanOrEqualTo(BAKLAVA)) {
      // From BAKLAVA the SDK_INT_FULL get stays.
      assertEquals(
          1,
          countStaticGets(
              inspector.clazz(TestClass.class).mainMethod(),
              inspector.getFactory().androidOsBuildVersionMembers.SDK_INT_FULL));
    } else {
      // Before BAKLAVA the SDK_INT_FULL static get is backported and the SDK_INT_FULL static get
      // is outlined from the backport as well. With just one use of the backport it is inlined
      // into main.
      ClassSubject backport =
          inspector.clazz(
              SyntheticItemsTestUtils.syntheticBackportWithForwardingClass(TestClass.class, 1));
      assertThat(backport, isAbsent());
      assertEquals(
          1,
          countStaticGets(
              inspector.clazz(TestClass.class).mainMethod(),
              inspector.getFactory().androidOsBuildVersionMembers.SDK_INT));
      assertEquals(
          0,
          countStaticGets(
              inspector.clazz(TestClass.class).mainMethod(),
              inspector.getFactory().androidOsBuildVersionMembers.SDK_INT_FULL));
      ClassSubject apiOutline =
          inspector.clazz(SyntheticItemsTestUtils.syntheticApiOutlineClass(TestClass.class, 0));
      assertThat(apiOutline.uniqueMethod(), isPresent());
      assertEquals(
          1,
          countStaticGets(
              apiOutline.uniqueMethod(),
              inspector.getFactory().androidOsBuildVersionMembers.SDK_INT_FULL));
    }
  }

  private long countStaticGets(MethodSubject subject, DexField field) {
    return subject
        .streamInstructions()
        .filter(InstructionSubject::isStaticGet)
        .map(InstructionSubject::getField)
        .filter(dexField -> dexField.isIdenticalTo(field))
        .count();
  }

  private static byte[] getTransformedMainClass() throws IOException {
    return transformer(TestClass.class)
        .replaceClassDescriptorInMethodInstructions(
            descriptor(VERSION.class), "Landroid/os/Build$VERSION;")
        .transform();
  }

  private byte[] getTransformedBuildVersionClass() throws IOException, NoSuchFieldException {
    return transformer(VERSION.class)
        .setClassDescriptor("Landroid/os/Build$VERSION;")
        .setAccessFlags(VERSION.class.getDeclaredField("SDK_INT"), AccessFlags::setFinal)
        .setAccessFlags(VERSION.class.getDeclaredField("SDK_INT_FULL"), AccessFlags::setFinal)
        .transform();
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(VERSION.SDK_INT_FULL);
    }
  }

  public static class /*android.os.Build$*/ VERSION {

    public static /*final*/ int SDK_INT = -1;
    public static /*final*/ int SDK_INT_FULL = -1;
  }
}
