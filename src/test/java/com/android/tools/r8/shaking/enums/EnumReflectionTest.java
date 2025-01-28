// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.enums;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.shaking.enums.EnumReflectionTest.Helpers.EnumA;
import com.android.tools.r8.shaking.enums.EnumReflectionTest.Helpers.EnumB;
import com.android.tools.r8.shaking.enums.EnumReflectionTest.Helpers.EnumC;
import com.android.tools.r8.shaking.enums.EnumReflectionTest.Helpers.EnumD;
import com.android.tools.r8.shaking.enums.EnumReflectionTest.Helpers.EnumE;
import com.android.tools.r8.shaking.enums.EnumReflectionTest.Helpers.EnumF;
import com.android.tools.r8.shaking.enums.EnumReflectionTest.Helpers.EnumG;
import com.android.tools.r8.shaking.enums.EnumReflectionTest.Helpers.EnumH;
import com.android.tools.r8.shaking.enums.EnumReflectionTest.Helpers.EnumI;
import com.android.tools.r8.shaking.enums.EnumReflectionTest.Helpers.EnumJ;
import com.android.tools.r8.shaking.enums.EnumReflectionTest.Helpers.EnumK;
import com.android.tools.r8.shaking.enums.EnumReflectionTest.Helpers.EnumL;
import com.android.tools.r8.shaking.enums.EnumReflectionTest.Helpers.EnumM;
import com.android.tools.r8.shaking.enums.EnumReflectionTest.Helpers.EnumN;
import com.android.tools.r8.shaking.enums.EnumReflectionTest.Helpers.EnumO;
import com.android.tools.r8.shaking.enums.EnumReflectionTest.Helpers.EnumP;
import com.android.tools.r8.shaking.enums.EnumReflectionTest.Helpers.EnumQ;
import com.android.tools.r8.shaking.enums.EnumReflectionTest.Helpers.EnumR;
import com.android.tools.r8.shaking.enums.EnumReflectionTest.Helpers.EnumS;
import com.android.tools.r8.shaking.enums.EnumReflectionTest.Helpers.EnumT;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Tests for the various APIs that cause an Enum type's values() method to be kept. */
@RunWith(Parameterized.class)
public class EnumReflectionTest extends TestBase {

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
          "none: [A, B]",
          "all: [B, C]",
          "of: [C]",
          "of: [D, E]",
          "of: [E, F, G]",
          "of: [F, G, H, I]",
          "of: [G, H, I, J, K]",
          "of: [H, I, J, K, L, M]",
          "range: [I, J]",
          "map: {J=1}",
          "valueOf: K",
          "bundle: L",
          "bundle: M",
          "parcel: N",
          "parcel: O",
          "intent: P",
          "intent: Q",
          "stream: R",
          "array: [S]",
          "array: [T]",
          "phi: [B]");

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
      E,
      F,
      G
    }

    public enum EnumF {
      F,
      G,
      H,
      I
    }

    public enum EnumG {
      G,
      H,
      I,
      J,
      K
    }

    public enum EnumH {
      H,
      I,
      J,
      K,
      L,
      M
    }

    public enum EnumI {
      I,
      J
    }

    public enum EnumJ {
      J,
      K
    }

    public enum EnumK {
      K,
      L
    }

    public enum EnumL {
      L,
      M
    }

    public enum EnumM {
      M,
      N
    }

    public enum EnumN {
      N,
      O
    }

    public enum EnumO {
      O,
      P
    }

    public enum EnumP {
      P,
      Q
    }

    public enum EnumQ {
      Q,
      R
    }

    public enum EnumR {
      R {}, // Test anonymous enum subtype.
      S
    }

    public enum EnumS {
      S,
      T
    }

    public enum EnumT {
      T,
      U
    }
  }

  public static class TestMain {
    @NeverInline
    private static void noneOf() {
      System.out.println("none: " + EnumSet.complementOf(EnumSet.noneOf(EnumA.class)));
    }

    @NeverInline
    private static void allOf() {
      System.out.println("all: " + EnumSet.allOf(EnumB.class));
    }

    @NeverInline
    private static void of1() {
      System.out.println("of: " + EnumSet.of(EnumC.C));
    }

    @NeverInline
    private static void of2() {
      System.out.println("of: " + EnumSet.of(EnumD.D, EnumD.E));
    }

    @NeverInline
    private static void of3() {
      System.out.println("of: " + EnumSet.of(EnumE.E, EnumE.F, EnumE.G));
    }

    @NeverInline
    private static void of4() {
      System.out.println("of: " + EnumSet.of(EnumF.F, EnumF.G, EnumF.H, EnumF.I));
    }

    @NeverInline
    private static void of5() {
      System.out.println("of: " + EnumSet.of(EnumG.G, EnumG.H, EnumG.I, EnumG.J, EnumG.K));
    }

    @NeverInline
    private static void ofVarArgs() {
      System.out.println("of: " + EnumSet.of(EnumH.H, EnumH.I, EnumH.J, EnumH.K, EnumH.L, EnumH.M));
    }

    @NeverInline
    private static void range() {
      System.out.println("range: " + EnumSet.range(EnumI.I, EnumI.J));
    }

    @NeverInline
    private static void map() {
      EnumMap<EnumJ, Integer> map = new EnumMap<>(EnumJ.class);
      map.put(EnumJ.J, 1);
      System.out.println("map: " + map);
    }

    @NeverInline
    private static void valueOf() {
      System.out.println("valueOf: " + EnumK.valueOf("K"));
    }

    @NeverInline
    private static void androidBundle1() {
      FakeBundle b = FakeBundle.createWithSingleSerializable(EnumL.L);
      EnumL result = (EnumL) b.getSerializable("");
      System.out.println("bundle: " + result);
    }

    @NeverInline
    private static void androidBundle2() {
      FakeBundle b = FakeBundle.createWithSingleSerializable(EnumM.M);
      EnumM result = b.getSerializable("", EnumM.class);
      System.out.println("bundle: " + result);
    }

    @NeverInline
    private static void androidParcel1() {
      FakeParcel p = FakeParcel.createWithSingleSerializable(EnumN.N);
      EnumN result = (EnumN) p.readSerializable();
      System.out.println("parcel: " + result);
    }

    @NeverInline
    private static void androidParcel2() {
      FakeParcel p = FakeParcel.createWithSingleSerializable(EnumO.O);
      System.out.println("parcel: " + p.readSerializable(null, EnumO.class));
    }

    @NeverInline
    private static void androidIntent1() {
      FakeIntent i = FakeIntent.createWithSingleSerializable(EnumP.P);
      EnumP result = (EnumP) i.getSerializableExtra("");
      System.out.println("intent: " + result);
    }

    @NeverInline
    private static void androidIntent2() {
      FakeIntent i = FakeIntent.createWithSingleSerializable(EnumQ.Q);
      System.out.println("intent: " + i.getSerializableExtra("", EnumQ.class));
    }

    @NeverInline
    private static void array1() {
      FakeParcel p = FakeParcel.createWithSingleSerializable(new EnumS[] {EnumS.S});
      EnumS[] result = (EnumS[]) p.readSerializable();
      System.out.println("array: " + Arrays.toString(result));
    }

    @NeverInline
    private static void array2() {
      FakeParcel p = FakeParcel.createWithSingleSerializable(new EnumT[] {EnumT.T});
      System.out.println("array: " + Arrays.toString(p.readSerializable(null, EnumT[].class)));
    }

    @NeverInline
    private static void objectStream() {
      try {
        ObjectInputStream objectInputStream =
            new ObjectInputStream(new ByteArrayInputStream(FakeParcel.toBytes(EnumR.R)));
        EnumR result = (EnumR) objectInputStream.readObject();
        System.out.println("stream: " + result);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    public static void main(String[] args) {
      // Use different methods to ensure Enqueuer.traceInvokeStatic() triggers for each one.
      noneOf();
      allOf();
      of1();
      of2();
      of3();
      of4();
      of5();
      ofVarArgs();
      range();
      map();
      valueOf();
      androidBundle1();
      androidBundle2();
      androidParcel1();
      androidParcel2();
      androidIntent1();
      androidIntent2();
      objectStream();
      array1();
      array2();
      // Ensure phi as argument does not cause issues.
      System.out.println(
          "phi: " + EnumSet.of((Enum) (args.length > 10 ? (Object) EnumA.A : (Object) EnumB.B)));
    }
  }

  // EnumR.R references this class in its constructor.
  private static final String ENUM_SUBTYPE_BRIDGE_CLASS_NAME =
      EnumReflectionTest.class.getName() + "$1";

  private static final String PARCEL_DESCRIPTOR = "Landroid/os/Parcel;";
  private static final String BUNDLE_DESCRIPTOR = "Landroid/os/Bundle;";
  private static final String INTENT_DESCRIPTOR = "Landroid/content/Intent;";

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
        .addOptionsModification(options -> options.experimentalTraceEnumReflection = true)
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
