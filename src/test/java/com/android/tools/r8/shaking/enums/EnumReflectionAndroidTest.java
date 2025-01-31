// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.enums;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.shaking.enums.EnumReflectionAndroidTest.Helpers.EnumA;
import com.android.tools.r8.shaking.enums.EnumReflectionAndroidTest.Helpers.EnumB;
import com.android.tools.r8.shaking.enums.EnumReflectionAndroidTest.Helpers.EnumC;
import com.android.tools.r8.shaking.enums.EnumReflectionAndroidTest.Helpers.EnumD;
import com.android.tools.r8.shaking.enums.EnumReflectionAndroidTest.Helpers.EnumE;
import com.android.tools.r8.shaking.enums.EnumReflectionAndroidTest.Helpers.EnumF;
import com.android.tools.r8.shaking.enums.EnumReflectionAndroidTest.Helpers.EnumG;
import com.android.tools.r8.shaking.enums.EnumReflectionAndroidTest.Helpers.EnumH;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Tests for the Android-specific APIs that cause an Enum type's values() method to be kept. */
@RunWith(Parameterized.class)
public class EnumReflectionAndroidTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withMaximumApiLevel().build();
  }

  public static class FakeParcel {
    // Deserializing arrays yields UnsatisfiedLinkError for com.android.org.conscrypt.NativeCrypto
    // when running under ART.
    private Class arrayType;
    private ObjectInputStream objectInputStream;

    public static byte[] toBytes(Serializable thing) {
      try {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
        objectOutputStream.writeObject(thing);
        return byteArrayOutputStream.toByteArray();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    public static FakeParcel createWithSingleSerializable(Serializable thing) {
      try {
        FakeParcel ret = new FakeParcel();
        if (thing.getClass().isArray()) {
          ret.arrayType = thing.getClass();
          if (Array.getLength(thing) != 1) {
            throw new IllegalArgumentException();
          }
          thing = (Serializable) Array.get(thing, 0);
        }
        ret.objectInputStream = new ObjectInputStream(new ByteArrayInputStream(toBytes(thing)));
        return ret;
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    public Serializable readSerializable() {
      try {
        Serializable ret = (Serializable) objectInputStream.readObject();
        if (arrayType != null) {
          Object array = Array.newInstance(arrayType.getComponentType(), 1);
          Array.set(array, 0, ret);
          ret = (Serializable) array;
        }
        return ret;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    public <T extends Serializable> T readSerializable(ClassLoader loader, Class<T> clazz) {
      return clazz.cast(readSerializable());
    }
  }

  public static class FakeBundle {
    private Object parcel;

    public static FakeBundle createWithSingleSerializable(Serializable thing) {
      FakeBundle ret = new FakeBundle();
      ret.parcel = FakeParcel.createWithSingleSerializable(thing);
      return ret;
    }

    public <T extends Serializable> T getSerializable(String key, Class<T> clazz) {
      return clazz.cast(getSerializable(key));
    }

    public Serializable getSerializable(String key) {
      return ((FakeParcel) parcel).readSerializable();
    }
  }

  public static class FakeIntent {
    private Object parcel;

    public static FakeIntent createWithSingleSerializable(Serializable thing) {
      FakeIntent ret = new FakeIntent();
      ret.parcel = FakeParcel.createWithSingleSerializable(thing);
      return ret;
    }

    public <T extends Serializable> T getSerializableExtra(String key, Class<T> clazz) {
      return clazz.cast(getSerializableExtra(key));
    }

    public Serializable getSerializableExtra(String key) {
      return ((FakeParcel) parcel).readSerializable();
    }
  }

  private static final List<String> EXPECTED_OUTPUT =
      Arrays.asList(
          "bundle: A",
          "bundle: B",
          "parcel: C",
          "parcel: D",
          "intent: E",
          "intent: F",
          "array: [G]",
          "array: [H]");

  public static class Helpers {

    public enum EnumA {
      A,
      B
    }

    public enum EnumB {
      B,
      C
    }

    public enum EnumC {
      C,
      D
    }

    public enum EnumD {
      D,
      E
    }

    public enum EnumE {
      E {},
      F,
    }

    public enum EnumF {
      F,
      G,
    }

    public enum EnumG {
      G {},
      H,
    }

    public enum EnumH {
      H,
      I,
    }
  }

  public static class TestMain {
    @NeverInline
    private static void androidBundle1() {
      FakeBundle b = FakeBundle.createWithSingleSerializable(EnumA.A);
      EnumA result = (EnumA) b.getSerializable("");
      System.out.println("bundle: " + result);
    }

    @NeverInline
    private static void androidBundle2() {
      FakeBundle b = FakeBundle.createWithSingleSerializable(EnumB.B);
      EnumB result = b.getSerializable("", EnumB.class);
      System.out.println("bundle: " + result);
    }

    @NeverInline
    private static void androidParcel1() {
      FakeParcel p = FakeParcel.createWithSingleSerializable(EnumC.C);
      EnumC result = (EnumC) p.readSerializable();
      System.out.println("parcel: " + result);
    }

    @NeverInline
    private static void androidParcel2() {
      FakeParcel p = FakeParcel.createWithSingleSerializable(EnumD.D);
      System.out.println("parcel: " + p.readSerializable(null, EnumD.class));
    }

    @NeverInline
    private static void androidIntent1() {
      FakeIntent i = FakeIntent.createWithSingleSerializable(EnumE.E);
      EnumE result = (EnumE) i.getSerializableExtra("");
      System.out.println("intent: " + result);
    }

    @NeverInline
    private static void androidIntent2() {
      FakeIntent i = FakeIntent.createWithSingleSerializable(EnumF.F);
      System.out.println("intent: " + i.getSerializableExtra("", EnumF.class));
    }

    @NeverInline
    private static void array1() {
      FakeParcel p = FakeParcel.createWithSingleSerializable(new EnumG[] {EnumG.G});
      EnumG[] result = (EnumG[]) p.readSerializable();
      System.out.println("array: " + Arrays.toString(result));
    }

    @NeverInline
    private static void array2() {
      FakeParcel p = FakeParcel.createWithSingleSerializable(new EnumH[] {EnumH.H});
      System.out.println("array: " + Arrays.toString(p.readSerializable(null, EnumH[].class)));
    }

    public static void main(String[] args) {
      // Use different methods to ensure Enqueuer.traceInvokeStatic() triggers for each one.
      androidBundle1();
      androidBundle2();
      androidParcel1();
      androidParcel2();
      androidIntent1();
      androidIntent2();
      array1();
      array2();
    }
  }

  private static final String PARCEL_DESCRIPTOR = "Landroid/os/Parcel;";
  private static final String BUNDLE_DESCRIPTOR = "Landroid/os/Bundle;";
  private static final String INTENT_DESCRIPTOR = "Landroid/content/Intent;";
  // EnumE.E and EnumG.G references this class in its constructor.
  private static final String ENUM_SUBTYPE_BRIDGE_CLASS_NAME =
      EnumReflectionAndroidTest.class.getName() + "$1";

  private static byte[] rewriteTestMain() throws IOException {
    return transformer(TestMain.class)
        .replaceClassDescriptorInMethodInstructions(descriptor(FakeParcel.class), PARCEL_DESCRIPTOR)
        .replaceClassDescriptorInMethodInstructions(descriptor(FakeBundle.class), BUNDLE_DESCRIPTOR)
        .replaceClassDescriptorInMethodInstructions(descriptor(FakeIntent.class), INTENT_DESCRIPTOR)
        .transform();
  }

  private static byte[] rewriteParcel() throws IOException {
    return transformer(FakeParcel.class).setClassDescriptor(PARCEL_DESCRIPTOR).transform();
  }

  private static byte[] rewriteBundle() throws IOException {
    return transformer(FakeBundle.class)
        .setClassDescriptor(BUNDLE_DESCRIPTOR)
        .replaceClassDescriptorInMethodInstructions(descriptor(FakeParcel.class), PARCEL_DESCRIPTOR)
        .transform();
  }

  private static byte[] rewriteIntent() throws IOException {
    return transformer(FakeIntent.class)
        .setClassDescriptor(INTENT_DESCRIPTOR)
        .replaceClassDescriptorInMethodInstructions(descriptor(FakeParcel.class), PARCEL_DESCRIPTOR)
        .transform();
  }

  @Test
  public void testRuntime() throws Exception {
    byte[] parcelBytes = rewriteParcel();
    byte[] bundleBytes = rewriteBundle();
    byte[] intentBytes = rewriteIntent();
    testForRuntime(parameters)
        .addProgramClassesAndInnerClasses(Helpers.class)
        .addProgramClassFileData(rewriteTestMain())
        .addProgramClasses(Class.forName(ENUM_SUBTYPE_BRIDGE_CLASS_NAME))
        .addClasspathClassFileData(parcelBytes, bundleBytes, intentBytes)
        .addRunClasspathFiles(buildOnDexRuntime(parameters, parcelBytes, bundleBytes, intentBytes))
        .run(parameters.getRuntime(), TestMain.class)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    byte[] parcelBytes = rewriteParcel();
    byte[] bundleBytes = rewriteBundle();
    byte[] intentBytes = rewriteIntent();
    testForR8(parameters.getBackend())
        .setMinApi(parameters)
        .addOptionsModification(options -> options.experimentalTraceAndroidEnumSerialization = true)
        .addProgramClassesAndInnerClasses(Helpers.class)
        .addProgramClassFileData(rewriteTestMain())
        .addProgramClasses(Class.forName(ENUM_SUBTYPE_BRIDGE_CLASS_NAME))
        .addClasspathClassFileData(parcelBytes, bundleBytes, intentBytes)
        .enableInliningAnnotations()
        .addKeepMainRule(TestMain.class)
        .compile()
        .addRunClasspathClassFileData(parcelBytes, bundleBytes, intentBytes)
        .run(parameters.getRuntime(), TestMain.class)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }
}
