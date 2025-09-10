// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.android.tools.r8.ByteBufferProvider;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DexOutputBufferTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public DexOutputBufferTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testNonExpandableWrapperByte() {
    DexOutputBuffer buffer = new DexOutputBuffer(new ByteBufferProvider() {});
    DexOutputBuffer wrapper1 = buffer.nonExpandableWrapper();
    DexOutputBuffer wrapper2 = buffer.nonExpandableWrapper();
    int defaultBufferSize = 256 * 1024; // private DEFAULT_BUFFER_SIZE in DexOutputBuffer.
    for (int i = 0; i < defaultBufferSize; i++) {
      wrapper1.putByte((byte) 1);
      assertEquals(1, wrapper2.get());
    }
    try {
      wrapper1.putByte((byte) 1);
      fail("Unexpected successful operation.");
    } catch (UnsupportedOperationException e) {
      // Expected.
    }
    try {
      wrapper2.putByte((byte) 1);
      fail("Unexpected successful operation.");
    } catch (UnsupportedOperationException e) {
      // Expected.
    }
  }

  @Test
  public void testNonExpandableWrapperShort() {
    DexOutputBuffer buffer = new DexOutputBuffer(new ByteBufferProvider() {});
    DexOutputBuffer wrapper1 = buffer.nonExpandableWrapper();
    DexOutputBuffer wrapper2 = buffer.nonExpandableWrapper();
    int defaultBufferSize = 256 * 1024; // private DEFAULT_BUFFER_SIZE in DexOutputBuffer.
    for (int i = 0; i < defaultBufferSize; i += 2 * 2) {
      wrapper1.putShort(Short.MAX_VALUE);
      assertEquals(Short.MAX_VALUE, wrapper2.getShort());
      wrapper1.putShort(Short.MIN_VALUE);
      assertEquals(Short.MIN_VALUE, wrapper2.getShort());
    }
    try {
      wrapper1.putShort((short) 1);
      fail("Unexpected successful operation.");
    } catch (UnsupportedOperationException e) {
      // Expected.
    }
    try {
      wrapper2.putShort((short) 1);
      fail("Unexpected successful operation.");
    } catch (UnsupportedOperationException e) {
      // Expected.
    }
  }
}
