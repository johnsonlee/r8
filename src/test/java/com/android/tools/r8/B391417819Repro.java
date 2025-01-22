// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.utils.StringUtils;
import java.util.BitSet;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class B391417819Repro extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static final String EXPECTED_OUTPUT = StringUtils.lines("Hello, world!");

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), TestClass.class)
        .assertFailureWithErrorThatThrows(ArrayIndexOutOfBoundsException.class);
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertFailureWithErrorThatThrows(ArrayIndexOutOfBoundsException.class);
  }

  @Test
  public void testR8() throws Exception {
    Assert.assertThrows(
        CompilationFailedException.class,
        () ->
            testForR8(parameters.getBackend())
                .addInnerClasses(getClass())
                .addKeepMainRule(TestClass.class)
                .setMinApi(parameters)
                .run(parameters.getRuntime(), TestClass.class)
                .assertSuccessWithOutput(EXPECTED_OUTPUT));
  }

  static class TestClass {

    public byte[] toBytes(boolean on) {
      byte[] bytes = new byte[3];
      bytes[0] = 1;
      BitSet bits = new BitSet(8);
      byte[] byteArray = bits.toByteArray();
      if (byteArray != null && byteArray.length >= 1) {
        bytes[1] = bits.toByteArray()[0];
      }
      BitSet bitSet = new BitSet(8);
      // Throws ArrayIndexOutOfBoundsException here, as the returned byte array from
      // bitSet.toByteArray() has length zero.
      bytes[2] = bitSet.toByteArray()[0];
      return bytes;
    }

    public static void print(byte[] b) {
      for (int i = 0; i < b.length; i++) {
        System.out.println(i + ": " + b[i]);
      }
    }

    public static void main(String[] args) {
      print(new TestClass().toBytes(System.currentTimeMillis() >= 0));
      print(new TestClass().toBytes(System.currentTimeMillis() < 0));
    }
  }
}
