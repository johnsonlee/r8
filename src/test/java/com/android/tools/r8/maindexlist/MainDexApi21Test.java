// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.maindexlist;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

// Test for allowing main-dex support for API 21 / L (See b//320893283).
@RunWith(Parameterized.class)
public class MainDexApi21Test extends TestBase {

  static class TestClassA {}

  static class TestClassB {}

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public MainDexApi21Test(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  static class TestConsumer extends DexIndexedConsumer.ForwardingConsumer {

    Map<Integer, byte[]> data = new HashMap<>();
    Map<Integer, Set<String>> descriptors = new HashMap<>();

    public TestConsumer() {
      super(null);
    }

    @Override
    public synchronized void accept(
        int fileIndex, ByteDataView data, Set<String> descriptors, DiagnosticsHandler handler) {
      assertNull(this.data.put(fileIndex, data.copyByteData()));
      assertNull(this.descriptors.put(fileIndex, descriptors));
    }
  }

  @Test
  public void testWithoutMainDexInputs() throws Exception {
    // Test to ensure that running at API 21 / L without any main-dex content works.
    // Since L does support native multdex the compiler should not require any main-dex inputs.
    TestConsumer programConsumer = new TestConsumer();
    testForD8()
        .setMinApi(AndroidApiLevel.L)
        .addProgramClasses(ImmutableList.of(TestClassA.class, TestClassB.class))
        .setProgramConsumer(programConsumer)
        .compile();
    assertEquals(1, programConsumer.data.size());
    assertEquals(1, programConsumer.descriptors.size());
    assertEquals(
        ImmutableSet.of(descriptor(TestClassA.class), descriptor(TestClassB.class)),
        programConsumer.descriptors.get(0));
  }

  @Test
  public void testWithMainDexRules() throws Exception {
    // Test to ensure that running at API 21 / L with main-dex rules will actually produce a
    // main-dex file. Debug mode will compile to a minimal main-dex, so we will observe two files.
    TestConsumer programConsumer = new TestConsumer();
    testForD8()
        .setMinApi(AndroidApiLevel.L)
        .addProgramClasses(ImmutableList.of(TestClassA.class, TestClassB.class))
        .addMainDexKeepClassAndMemberRules(TestClassB.class)
        .setProgramConsumer(programConsumer)
        .compile();
    assertEquals(2, programConsumer.data.size());
    assertEquals(2, programConsumer.descriptors.size());
    assertEquals(ImmutableSet.of(descriptor(TestClassB.class)), programConsumer.descriptors.get(0));
    assertEquals(ImmutableSet.of(descriptor(TestClassA.class)), programConsumer.descriptors.get(1));
  }

  @Test
  public void testWithMainDexList() throws Exception {
    // Test to ensure that running at API 21 / L with main-dex list will actually produce a
    // main-dex file. Debug mode will compile to a minimal main-dex, so we will observe two files.
    TestConsumer programConsumer = new TestConsumer();
    testForD8()
        .setMinApi(AndroidApiLevel.L)
        .addProgramClasses(ImmutableList.of(TestClassA.class, TestClassB.class))
        .addMainDexListClasses(TestClassB.class)
        .setProgramConsumer(programConsumer)
        .compile();
    assertEquals(2, programConsumer.data.size());
    assertEquals(2, programConsumer.descriptors.size());
    assertEquals(ImmutableSet.of(descriptor(TestClassB.class)), programConsumer.descriptors.get(0));
    assertEquals(ImmutableSet.of(descriptor(TestClassA.class)), programConsumer.descriptors.get(1));
  }
}
