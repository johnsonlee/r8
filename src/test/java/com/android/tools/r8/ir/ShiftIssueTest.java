// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ShiftIssueTest extends TestBase {

  private static final String EXPECTED_RESULT = StringUtils.lines("7", "7", "7", "3", "3", "14");
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimes().withDexRuntimes().withAllApiLevels().build();
  }

  public ShiftIssueTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testD8() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .allowStdoutMessages()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  static class Main {
    public static void main(String[] args) {
      ushr32();
      shr32();
      shl32();
      ushr33();
      shr33();
      shl33();
    }

    @NeverInline
    private static void ushr32() {
      int d = 1;
      for (int g = 0; g < 10; g++) {
        d = 7;
      }
      d >>>= 32;
      System.out.println(d);
    }

    @NeverInline
    private static void shr32() {
      int d = 1;
      for (int g = 0; g < 10; g++) {
        d = 7;
      }
      d >>= 32;
      System.out.println(d);
    }

    @NeverInline
    private static void shl32() {
      int d = 1;
      for (int g = 0; g < 10; g++) {
        d = 7;
      }
      d <<= 32;
      System.out.println(d);
    }

    @NeverInline
    private static void ushr33() {
      int d = 1;
      for (int g = 0; g < 10; g++) {
        d = 7;
      }
      d >>>= 33;
      System.out.println(d);
    }

    @NeverInline
    private static void shr33() {
      int d = 1;
      for (int g = 0; g < 10; g++) {
        d = 7;
      }
      d >>= 33;
      System.out.println(d);
    }

    @NeverInline
    private static void shl33() {
      int d = 1;
      for (int g = 0; g < 10; g++) {
        d = 7;
      }
      d <<= 33;
      System.out.println(d);
    }
  }
}
