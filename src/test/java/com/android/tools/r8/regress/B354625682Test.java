// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress;

import static org.junit.Assert.assertThrows;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.google.common.collect.ImmutableList;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class B354625682Test extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static final List<String> EXPECTED_OUTPUT = ImmutableList.of("over");

  @Test
  public void testD8AndJvm() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    // TODO(b/354625682): Should run successfully.
    assertThrows(
        AssertionError.class,
        () ->
            testForR8(parameters.getBackend())
                .addInnerClasses(getClass())
                .addKeepMainRule(TestClass.class)
                .setMinApi(parameters)
                .run(parameters.getRuntime(), TestClass.class)
                .assertSuccessWithOutputLines(EXPECTED_OUTPUT));
  }

  static class TestClass {

    void a(String[] b) {
      ByteBuffer byteBuffer = null;
      SocketAddress socketAddress = null;
      DatagramChannel datagramChannel = null;
      try {
        if (socketAddress == null) {
          datagramChannel.receive(byteBuffer);
        }
      } catch (Throwable $) {
      }
    }

    public static void main(String[] c) {
      TestClass e = new TestClass();
      e.a(c);
      System.out.println("over");
    }
  }
}
