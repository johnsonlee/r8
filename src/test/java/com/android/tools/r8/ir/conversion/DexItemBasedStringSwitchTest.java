// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DexItemBasedStringSwitchTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepRules("-repackageclasses")
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A");
  }

  static class Main {

    public static void main(String[] args) {
      Object o = System.currentTimeMillis() > 0 ? new A() : new B();
      switch (o.getClass().getName()) {
        case "com.android.tools.r8.ir.conversion.DexItemBasedStringSwitchTest$A":
          System.out.println("A");
          return;
        case "com.android.tools.r8.ir.conversion.DexItemBasedStringSwitchTest$B":
          System.out.println("B");
          break;
        default:
          System.out.println("Neither");
          break;
      }
    }
  }

  @NoHorizontalClassMerging
  static class A {}

  @NoHorizontalClassMerging
  static class B {}
}
