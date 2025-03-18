// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk21.autocloseable;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AutoCloseableOtherSubtypeTest extends TestBase {

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntimes()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK21)
        .withApiLevelsStartingAtIncluding(AndroidApiLevel.K)
        .enableApiLevelsForCf()
        .build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .run(parameters.getRuntime(), TestRunner.class)
        .assertSuccessWithOutputLines("CLOSE", "SUCCESS");
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .compile()
        .inspect(this::assertNoExtraAutoCloseable)
        .run(parameters.getRuntime(), TestRunner.class)
        .assertSuccessWithOutputLines("CLOSE", "SUCCESS");
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .addKeepMainRule(TestRunner.class)
        .run(parameters.getRuntime(), TestRunner.class)
        .assertSuccessWithOutputLines("CLOSE", "SUCCESS");
  }

  private void assertNoExtraAutoCloseable(CodeInspector inspector) {
    assertTrue(inspector.clazz(MyInputStream.class).getDexProgramClass().getInterfaces().isEmpty());
  }

  public static class MyInputStream extends InputStream {

    @Override
    public void close() throws IOException {
      System.out.println("CLOSE");
      super.close();
    }

    @Override
    public int read() throws IOException {
      return 0;
    }
  }

  public static class TestRunner {
    public static void main(String[] args) throws IOException {
      MyInputStream myInputStream = new MyInputStream();
      myInputStream.close();
      System.out.println("SUCCESS");
    }
  }
}
