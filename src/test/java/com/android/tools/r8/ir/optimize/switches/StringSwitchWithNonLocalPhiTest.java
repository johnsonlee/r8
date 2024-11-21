// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.switches;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StringSwitchWithNonLocalPhiTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addProgramClasses(Main.class)
        .release()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Foo", "1", "Bar", "2", "Baz", "0");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Foo", "1", "Bar", "2", "Baz", "0");
  }

  static class Main {

    public static void main(String[] args) {
      test("Foo");
      test("Bar");
      test("Baz");
    }

    static void test(String str) {
      int hashCode = str.hashCode();
      int id = 0;
      switch (hashCode) {
        case 70822: // "Foo".hashCode()
          if (str.equals("Foo")) {
            id = 1;
          }
          break;
        case 66547: // "Bar".hashCode()
          if (str.equals("Bar")) {
            id = 2;
          }
          break;
      }
      switch (id) {
        case 1:
          System.out.println("Foo");
          break;
        case 2:
          System.out.println("Bar");
          break;
        default:
          System.out.println("Baz");
          break;
      }
      System.out.println(id);
    }
  }
}
