// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk11.buffercovariantreturntype;

import static com.android.tools.r8.TestRuntime.CfVm.JDK11;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BufferCovariantReturnTypeTest extends TestBase {

  private static final String EXPECTED_RESULT_PER_BUFFER =
      StringUtils.lines("5", "2", "5", "0", "16", "0", "0", "2");
  private static final String EXPECTED_RESULT =
      new String(new char[14]).replace("\0", EXPECTED_RESULT_PER_BUFFER);

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(JDK11)
        .withDexRuntimes()
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  @Test
  public void testJVM() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .run(parameters.getRuntime(), BufferCovariantReturnTypeMain.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addInnerClassesAndStrippedOuter(getClass())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), BufferCovariantReturnTypeMain.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addInnerClassesAndStrippedOuter(getClass())
        .addKeepMainRule(BufferCovariantReturnTypeMain.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), BufferCovariantReturnTypeMain.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  public static class BufferCovariantReturnTypeMain {

    public static void main(String[] args) {
      byteBufferTest();
      charBufferTest();
      shortBufferTest();
      intBufferTest();
      longBufferTest();
      floatBufferTest();
      doubleBufferTest();
    }

    static void byteBufferTest() {
      byte[] data = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
      byte putValue = 55;

      ByteBuffer directBuffer = ByteBuffer.wrap(data);
      Buffer indirectBuffer = ByteBuffer.wrap(data);
      ByteBuffer castedIndirectBuffer = (ByteBuffer) indirectBuffer;

      directBuffer = directBuffer.position(5);
      System.out.println(directBuffer.position()); // 5
      directBuffer = directBuffer.limit(7);
      System.out.println(directBuffer.remaining()); // 2
      directBuffer = directBuffer.mark().put(putValue).put(putValue).reset();
      System.out.println(directBuffer.position()); // 5
      directBuffer = directBuffer.clear();
      System.out.println(directBuffer.position()); // 0
      System.out.println(directBuffer.remaining()); // 16
      directBuffer.put(putValue);
      directBuffer.put(putValue);
      directBuffer = directBuffer.rewind();
      System.out.println(directBuffer.position()); // 0
      directBuffer.put(putValue);
      directBuffer.put(putValue);
      directBuffer = directBuffer.flip();
      System.out.println(directBuffer.position()); // 0
      System.out.println(directBuffer.remaining()); // 2

      indirectBuffer = indirectBuffer.position(5);
      System.out.println(indirectBuffer.position()); // 5
      indirectBuffer = indirectBuffer.limit(7);
      System.out.println(indirectBuffer.remaining()); // 2
      indirectBuffer = indirectBuffer.mark();
      castedIndirectBuffer.put(putValue);
      castedIndirectBuffer.put(putValue);
      indirectBuffer = indirectBuffer.reset();
      System.out.println(indirectBuffer.position()); // 5
      indirectBuffer = indirectBuffer.clear();
      System.out.println(indirectBuffer.position()); // 0
      System.out.println(indirectBuffer.remaining()); // 16
      castedIndirectBuffer.put(putValue);
      castedIndirectBuffer.put(putValue);
      indirectBuffer = indirectBuffer.rewind();
      System.out.println(indirectBuffer.position()); // 0
      castedIndirectBuffer.put(putValue);
      castedIndirectBuffer.put(putValue);
      indirectBuffer = indirectBuffer.flip();
      System.out.println(indirectBuffer.position()); // 0
      System.out.println(indirectBuffer.remaining()); // 2
    }

    static void charBufferTest() {
      char[] data = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
      char putValue = 55;

      CharBuffer directBuffer = CharBuffer.wrap(data);
      Buffer indirectBuffer = CharBuffer.wrap(data);
      CharBuffer castedIndirectBuffer = (CharBuffer) indirectBuffer;

      directBuffer = directBuffer.position(5);
      System.out.println(directBuffer.position()); // 5
      directBuffer = directBuffer.limit(7);
      System.out.println(directBuffer.remaining()); // 2
      directBuffer = directBuffer.mark().put(putValue).put(putValue).reset();
      System.out.println(directBuffer.position()); // 5
      directBuffer = directBuffer.clear();
      System.out.println(directBuffer.position()); // 0
      System.out.println(directBuffer.remaining()); // 16
      directBuffer.put(putValue);
      directBuffer.put(putValue);
      directBuffer = directBuffer.rewind();
      System.out.println(directBuffer.position()); // 0
      directBuffer.put(putValue);
      directBuffer.put(putValue);
      directBuffer = directBuffer.flip();
      System.out.println(directBuffer.position()); // 0
      System.out.println(directBuffer.remaining()); // 2

      indirectBuffer = indirectBuffer.position(5);
      System.out.println(indirectBuffer.position()); // 5
      indirectBuffer = indirectBuffer.limit(7);
      System.out.println(indirectBuffer.remaining()); // 2
      indirectBuffer = indirectBuffer.mark();
      castedIndirectBuffer.put(putValue);
      castedIndirectBuffer.put(putValue);
      indirectBuffer = indirectBuffer.reset();
      System.out.println(indirectBuffer.position()); // 5
      indirectBuffer = indirectBuffer.clear();
      System.out.println(indirectBuffer.position()); // 0
      System.out.println(indirectBuffer.remaining()); // 16
      castedIndirectBuffer.put(putValue);
      castedIndirectBuffer.put(putValue);
      indirectBuffer = indirectBuffer.rewind();
      System.out.println(indirectBuffer.position()); // 0
      castedIndirectBuffer.put(putValue);
      castedIndirectBuffer.put(putValue);
      indirectBuffer = indirectBuffer.flip();
      System.out.println(indirectBuffer.position()); // 0
      System.out.println(indirectBuffer.remaining()); // 2
    }

    static void shortBufferTest() {
      short[] data = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
      short putValue = 55;

      ShortBuffer directBuffer = ShortBuffer.wrap(data);
      Buffer indirectBuffer = ShortBuffer.wrap(data);
      ShortBuffer castedIndirectBuffer = (ShortBuffer) indirectBuffer;

      directBuffer = directBuffer.position(5);
      System.out.println(directBuffer.position()); // 5
      directBuffer = directBuffer.limit(7);
      System.out.println(directBuffer.remaining()); // 2
      directBuffer = directBuffer.mark().put(putValue).put(putValue).reset();
      System.out.println(directBuffer.position()); // 5
      directBuffer = directBuffer.clear();
      System.out.println(directBuffer.position()); // 0
      System.out.println(directBuffer.remaining()); // 16
      directBuffer.put(putValue);
      directBuffer.put(putValue);
      directBuffer = directBuffer.rewind();
      System.out.println(directBuffer.position()); // 0
      directBuffer.put(putValue);
      directBuffer.put(putValue);
      directBuffer = directBuffer.flip();
      System.out.println(directBuffer.position()); // 0
      System.out.println(directBuffer.remaining()); // 2

      indirectBuffer = indirectBuffer.position(5);
      System.out.println(indirectBuffer.position()); // 5
      indirectBuffer = indirectBuffer.limit(7);
      System.out.println(indirectBuffer.remaining()); // 2
      indirectBuffer = indirectBuffer.mark();
      castedIndirectBuffer.put(putValue);
      castedIndirectBuffer.put(putValue);
      indirectBuffer = indirectBuffer.reset();
      System.out.println(indirectBuffer.position()); // 5
      indirectBuffer = indirectBuffer.clear();
      System.out.println(indirectBuffer.position()); // 0
      System.out.println(indirectBuffer.remaining()); // 16
      castedIndirectBuffer.put(putValue);
      castedIndirectBuffer.put(putValue);
      indirectBuffer = indirectBuffer.rewind();
      System.out.println(indirectBuffer.position()); // 0
      castedIndirectBuffer.put(putValue);
      castedIndirectBuffer.put(putValue);
      indirectBuffer = indirectBuffer.flip();
      System.out.println(indirectBuffer.position()); // 0
      System.out.println(indirectBuffer.remaining()); // 2
    }

    static void intBufferTest() {
      int[] data = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
      int putValue = 55;

      IntBuffer directBuffer = IntBuffer.wrap(data);
      Buffer indirectBuffer = IntBuffer.wrap(data);
      IntBuffer castedIndirectBuffer = (IntBuffer) indirectBuffer;

      directBuffer = directBuffer.position(5);
      System.out.println(directBuffer.position()); // 5
      directBuffer = directBuffer.limit(7);
      System.out.println(directBuffer.remaining()); // 2
      directBuffer = directBuffer.mark().put(putValue).put(putValue).reset();
      System.out.println(directBuffer.position()); // 5
      directBuffer = directBuffer.clear();
      System.out.println(directBuffer.position()); // 0
      System.out.println(directBuffer.remaining()); // 16
      directBuffer.put(putValue);
      directBuffer.put(putValue);
      directBuffer = directBuffer.rewind();
      System.out.println(directBuffer.position()); // 0
      directBuffer.put(putValue);
      directBuffer.put(putValue);
      directBuffer = directBuffer.flip();
      System.out.println(directBuffer.position()); // 0
      System.out.println(directBuffer.remaining()); // 2

      indirectBuffer = indirectBuffer.position(5);
      System.out.println(indirectBuffer.position()); // 5
      indirectBuffer = indirectBuffer.limit(7);
      System.out.println(indirectBuffer.remaining()); // 2
      indirectBuffer = indirectBuffer.mark();
      castedIndirectBuffer.put(putValue);
      castedIndirectBuffer.put(putValue);
      indirectBuffer = indirectBuffer.reset();
      System.out.println(indirectBuffer.position()); // 5
      indirectBuffer = indirectBuffer.clear();
      System.out.println(indirectBuffer.position()); // 0
      System.out.println(indirectBuffer.remaining()); // 16
      castedIndirectBuffer.put(putValue);
      castedIndirectBuffer.put(putValue);
      indirectBuffer = indirectBuffer.rewind();
      System.out.println(indirectBuffer.position()); // 0
      castedIndirectBuffer.put(putValue);
      castedIndirectBuffer.put(putValue);
      indirectBuffer = indirectBuffer.flip();
      System.out.println(indirectBuffer.position()); // 0
      System.out.println(indirectBuffer.remaining()); // 2
    }

    static void longBufferTest() {
      long[] data = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
      long putValue = 55;

      LongBuffer directBuffer = LongBuffer.wrap(data);
      Buffer indirectBuffer = LongBuffer.wrap(data);
      LongBuffer castedIndirectBuffer = (LongBuffer) indirectBuffer;

      directBuffer = directBuffer.position(5);
      System.out.println(directBuffer.position()); // 5
      directBuffer = directBuffer.limit(7);
      System.out.println(directBuffer.remaining()); // 2
      directBuffer = directBuffer.mark().put(putValue).put(putValue).reset();
      System.out.println(directBuffer.position()); // 5
      directBuffer = directBuffer.clear();
      System.out.println(directBuffer.position()); // 0
      System.out.println(directBuffer.remaining()); // 16
      directBuffer.put(putValue);
      directBuffer.put(putValue);
      directBuffer = directBuffer.rewind();
      System.out.println(directBuffer.position()); // 0
      directBuffer.put(putValue);
      directBuffer.put(putValue);
      directBuffer = directBuffer.flip();
      System.out.println(directBuffer.position()); // 0
      System.out.println(directBuffer.remaining()); // 2

      indirectBuffer = indirectBuffer.position(5);
      System.out.println(indirectBuffer.position()); // 5
      indirectBuffer = indirectBuffer.limit(7);
      System.out.println(indirectBuffer.remaining()); // 2
      indirectBuffer = indirectBuffer.mark();
      castedIndirectBuffer.put(putValue);
      castedIndirectBuffer.put(putValue);
      indirectBuffer = indirectBuffer.reset();
      System.out.println(indirectBuffer.position()); // 5
      indirectBuffer = indirectBuffer.clear();
      System.out.println(indirectBuffer.position()); // 0
      System.out.println(indirectBuffer.remaining()); // 16
      castedIndirectBuffer.put(putValue);
      castedIndirectBuffer.put(putValue);
      indirectBuffer = indirectBuffer.rewind();
      System.out.println(indirectBuffer.position()); // 0
      castedIndirectBuffer.put(putValue);
      castedIndirectBuffer.put(putValue);
      indirectBuffer = indirectBuffer.flip();
      System.out.println(indirectBuffer.position()); // 0
      System.out.println(indirectBuffer.remaining()); // 2
    }

    static void floatBufferTest() {
      float[] data = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
      float putValue = 55;

      FloatBuffer directBuffer = FloatBuffer.wrap(data);
      Buffer indirectBuffer = FloatBuffer.wrap(data);
      FloatBuffer castedIndirectBuffer = (FloatBuffer) indirectBuffer;

      directBuffer = directBuffer.position(5);
      System.out.println(directBuffer.position()); // 5
      directBuffer = directBuffer.limit(7);
      System.out.println(directBuffer.remaining()); // 2
      directBuffer = directBuffer.mark().put(putValue).put(putValue).reset();
      System.out.println(directBuffer.position()); // 5
      directBuffer = directBuffer.clear();
      System.out.println(directBuffer.position()); // 0
      System.out.println(directBuffer.remaining()); // 16
      directBuffer.put(putValue);
      directBuffer.put(putValue);
      directBuffer = directBuffer.rewind();
      System.out.println(directBuffer.position()); // 0
      directBuffer.put(putValue);
      directBuffer.put(putValue);
      directBuffer = directBuffer.flip();
      System.out.println(directBuffer.position()); // 0
      System.out.println(directBuffer.remaining()); // 2

      indirectBuffer = indirectBuffer.position(5);
      System.out.println(indirectBuffer.position()); // 5
      indirectBuffer = indirectBuffer.limit(7);
      System.out.println(indirectBuffer.remaining()); // 2
      indirectBuffer = indirectBuffer.mark();
      castedIndirectBuffer.put(putValue);
      castedIndirectBuffer.put(putValue);
      indirectBuffer = indirectBuffer.reset();
      System.out.println(indirectBuffer.position()); // 5
      indirectBuffer = indirectBuffer.clear();
      System.out.println(indirectBuffer.position()); // 0
      System.out.println(indirectBuffer.remaining()); // 16
      castedIndirectBuffer.put(putValue);
      castedIndirectBuffer.put(putValue);
      indirectBuffer = indirectBuffer.rewind();
      System.out.println(indirectBuffer.position()); // 0
      castedIndirectBuffer.put(putValue);
      castedIndirectBuffer.put(putValue);
      indirectBuffer = indirectBuffer.flip();
      System.out.println(indirectBuffer.position()); // 0
      System.out.println(indirectBuffer.remaining()); // 2
    }

    static void doubleBufferTest() {
      double[] data = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
      double putValue = 55;

      DoubleBuffer directBuffer = DoubleBuffer.wrap(data);
      Buffer indirectBuffer = DoubleBuffer.wrap(data);
      DoubleBuffer castedIndirectBuffer = (DoubleBuffer) indirectBuffer;

      directBuffer = directBuffer.position(5);
      System.out.println(directBuffer.position()); // 5
      directBuffer = directBuffer.limit(7);
      System.out.println(directBuffer.remaining()); // 2
      directBuffer = directBuffer.mark().put(putValue).put(putValue).reset();
      System.out.println(directBuffer.position()); // 5
      directBuffer = directBuffer.clear();
      System.out.println(directBuffer.position()); // 0
      System.out.println(directBuffer.remaining()); // 16
      directBuffer.put(putValue);
      directBuffer.put(putValue);
      directBuffer = directBuffer.rewind();
      System.out.println(directBuffer.position()); // 0
      directBuffer.put(putValue);
      directBuffer.put(putValue);
      directBuffer = directBuffer.flip();
      System.out.println(directBuffer.position()); // 0
      System.out.println(directBuffer.remaining()); // 2

      indirectBuffer = indirectBuffer.position(5);
      System.out.println(indirectBuffer.position()); // 5
      indirectBuffer = indirectBuffer.limit(7);
      System.out.println(indirectBuffer.remaining()); // 2
      indirectBuffer = indirectBuffer.mark();
      castedIndirectBuffer.put(putValue);
      castedIndirectBuffer.put(putValue);
      indirectBuffer = indirectBuffer.reset();
      System.out.println(indirectBuffer.position()); // 5
      indirectBuffer = indirectBuffer.clear();
      System.out.println(indirectBuffer.position()); // 0
      System.out.println(indirectBuffer.remaining()); // 16
      castedIndirectBuffer.put(putValue);
      castedIndirectBuffer.put(putValue);
      indirectBuffer = indirectBuffer.rewind();
      System.out.println(indirectBuffer.position()); // 0
      castedIndirectBuffer.put(putValue);
      castedIndirectBuffer.put(putValue);
      indirectBuffer = indirectBuffer.flip();
      System.out.println(indirectBuffer.position()); // 0
      System.out.println(indirectBuffer.remaining()); // 2
    }
  }
}
