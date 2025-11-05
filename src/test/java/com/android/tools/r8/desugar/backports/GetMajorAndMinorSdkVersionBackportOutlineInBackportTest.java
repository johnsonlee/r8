// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.backports;

import static com.android.tools.r8.utils.AndroidApiLevel.BAKLAVA;
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
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
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
public class GetMajorAndMinorSdkVersionBackportOutlineInBackportTest extends TestBase {

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

  private DexMethod getGetMajorSdkVersion(CodeInspector inspector) {
    DexItemFactory factory = inspector.getFactory();
    DexType build = factory.androidOsBuildType;
    return factory.createMethod(
        build,
        factory.createProto(factory.intType, factory.intType),
        factory.createString("getMajorSdkVersion"));
  }

  private DexMethod getGetMinorSdkVersion(CodeInspector inspector) {
    DexItemFactory factory = inspector.getFactory();
    DexType build = factory.androidOsBuildType;
    return factory.createMethod(
        build,
        factory.createProto(factory.intType, factory.intType),
        factory.createString("getMinorSdkVersion"));
  }

  @Test
  public void testD8() throws Exception {
    for (AndroidApiLevel apiLevel : AndroidApiLevel.values()) {
      if (apiLevel.isGreaterThan(AndroidApiLevel.LATEST)) {
        continue;
      }
      testForD8()
          .addProgramClassFileData(getTransformedMainClass())
          .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.BAKLAVA))
          .collectSyntheticItems()
          .setMinApi(apiLevel)
          .compile()
          .inspectWithSyntheticItems(
              (inspector, syntheticItems) -> inspectD8(inspector, syntheticItems, apiLevel));
    }
  }

  @Test
  public void testR8() throws Exception {
    for (AndroidApiLevel apiLevel : AndroidApiLevel.values()) {
      // SDK_INT was introduced in D so don't bother testing before.
      if (apiLevel.isLessThanOrEqualTo(AndroidApiLevel.D)
          || apiLevel.isGreaterThan(AndroidApiLevel.LATEST)) {
        continue;
      }
      testForR8(Backend.DEX)
          .addProgramClassFileData(getTransformedMainClass())
          .addKeepMainRule(TestClass.class)
          .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.BAKLAVA))
          .collectSyntheticItems()
          .setMinApi(apiLevel)
          .addDontObfuscate()
          .compile()
          .inspectWithSyntheticItems(
              (inspector, syntheticItems) -> inspectR8(inspector, syntheticItems, apiLevel));
    }
  }

  private void inspectD8(
      CodeInspector inspector, SyntheticItemsTestUtils syntheticItems, AndroidApiLevel apiLevel) {
    if (apiLevel.isGreaterThanOrEqualTo(BAKLAVA)) {
      // From BAKLAVA calls to getMajorSdkVersion() and getMinorSdkVersion() stays.
      assertEquals(
          2,
          countInvokeStaticToMethod(
              inspector.clazz(TestClass.class).mainMethod(), getGetMajorSdkVersion(inspector)));
      assertEquals(
          2,
          countInvokeStaticToMethod(
              inspector.clazz(TestClass.class).mainMethod(), getGetMinorSdkVersion(inspector)));
    } else {
      // Before BAKLAVA the static methods getMajorSdkVersion() and getMinorSdkVersion()
      // are backported, and the invoke to these methods (which is part of the backport) is
      // outlined from the backport as well.
      ClassSubject apiOutline0 =
          inspector.clazz(syntheticItems.syntheticApiOutlineClass(TestClass.class, 0));
      assertEquals(
          1,
          countInvokeStaticToMethod(apiOutline0.uniqueMethod(), getGetMinorSdkVersion(inspector)));
      ClassSubject apiOutline1 =
          inspector.clazz(syntheticItems.syntheticApiOutlineClass(TestClass.class, 1));
      assertEquals(
          1,
          countInvokeStaticToMethod(apiOutline1.uniqueMethod(), getGetMajorSdkVersion(inspector)));
      for (int i = 2; i < 3; i++) {
        ClassSubject backport =
            inspector.clazz(
                syntheticItems.syntheticBackportWithForwardingClass(TestClass.class, i));
        assertThat(backport, isPresent());
        assertEquals(
            1,
            countStaticGets(
                backport.uniqueMethod(),
                inspector.getFactory().androidOsBuildVersionMembers.SDK_INT));
        assertEquals(
            0,
            countInvokeStaticToMethod(backport.uniqueMethod(), getGetMajorSdkVersion(inspector)));
        assertEquals(
            0,
            countInvokeStaticToMethod(backport.uniqueMethod(), getGetMinorSdkVersion(inspector)));
      }
    }
  }

  private void inspectR8(
      CodeInspector inspector, SyntheticItemsTestUtils syntheticItems, AndroidApiLevel apiLevel) {
    if (apiLevel.isGreaterThanOrEqualTo(BAKLAVA)) {
      // From BAKLAVA calls to getMajorSdkVersion() and getMinorSdkVersion() stays.
      assertEquals(
          2,
          countInvokeStaticToMethod(
              inspector.clazz(TestClass.class).mainMethod(), getGetMajorSdkVersion(inspector)));
      assertEquals(
          2,
          countInvokeStaticToMethod(
              inspector.clazz(TestClass.class).mainMethod(), getGetMinorSdkVersion(inspector)));
    } else {
      ClassSubject apiOutline0 =
          inspector.clazz(syntheticItems.syntheticApiOutlineClass(TestClass.class, 0));
      assertEquals(4, apiOutline0.allMethods().size());
      MethodSubject main = inspector.clazz(TestClass.class).mainMethod();
      assertEquals(
          4, countInvokeStaticToMethodHolder(main, apiOutline0.getDexProgramClass().getType()));
    }
  }

  private long countInvokeStaticToMethod(MethodSubject subject, DexMethod method) {
    return subject
        .streamInstructions()
        .filter(InstructionSubject::isInvokeStatic)
        .map(InstructionSubject::getMethod)
        .filter(dexMethod -> dexMethod.isIdenticalTo(method))
        .count();
  }

  private long countInvokeStaticToMethodHolder(MethodSubject subject, DexType holder) {
    return subject
        .streamInstructions()
        .filter(InstructionSubject::isInvokeStatic)
        .map(InstructionSubject::getMethod)
        .filter(dexMethod -> dexMethod.getHolderType().isIdenticalTo(holder))
        .count();
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
        .replaceClassDescriptorInMethodInstructions(descriptor(Build.class), "Landroid/os/Build;")
        .transform();
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(Build.getMajorSdkVersion(100_000));
      System.out.println(Build.getMinorSdkVersion(100_000));
      System.out.println(Build.getMajorSdkVersion(3500_000));
      System.out.println(Build.getMinorSdkVersion(3500_000));
    }
  }

  public static class /*android.os.*/ Build {

    public static int getMajorSdkVersion(int sdkIntFull) {
      throw new RuntimeException();
    }

    public static int getMinorSdkVersion(int sdkIntFull) {
      throw new RuntimeException();
    }
  }
}
