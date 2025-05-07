// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class CheckIndexTest extends TestBase {

  private static final String EXPECTED_RESULT = StringUtils.lines("1", "2", "3");
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimes().withDexRuntimes().withAllApiLevels().build();
  }

  public CheckIndexTest(TestParameters parameters) {
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
        .compile()
        .inspect(
            i ->
                Assert.assertTrue(
                    i.clazz(Main.class)
                        .uniqueMethodWithOriginalName("print")
                        .streamInstructions()
                        .noneMatch(InstructionSubject::isNewInstance)))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  static class Main {

    public static void main(String[] args) {
      print("a");
      print("b");
      print("g");
    }

    @NeverInline
    static void print(String s) {
      int res;
      switch (s) {
        case "a":
          res = 1;
          break;
        case "b":
          res = 2;
          break;
        default:
          res = 3;
      }
      if (res < 1) {
        throw new RuntimeException("Below 1!");
      }
      if (res > 3) {
        throw new RuntimeException("Above 3!");
      }
      System.out.println(res);
    }
  }
}
