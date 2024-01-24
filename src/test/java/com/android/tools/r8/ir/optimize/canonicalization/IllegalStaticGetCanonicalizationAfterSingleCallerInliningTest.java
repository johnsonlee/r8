// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.canonicalization;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IllegalStaticGetCanonicalizationAfterSingleCallerInliningTest extends TestBase {

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
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/321803511): Should succeed with expected output on DEX where we canonicalize
        //  constants.
        .assertSuccessWithOutputLines(parameters.isCfRuntime() ? "Hello, world!" : "null");
  }

  static class Main {

    static String f;

    public static void main(String[] args) {
      System.out.println(indirection());
    }

    // Indirection is needed to reproduce the bug because we only prune the test() method from the
    // compiler state after the caller wave has been processed.
    static String indirection() {
      return test();
    }

    // When single caller inlining test() into main() the field access collection must be updated
    // in a way so that `f` is not treated as being effectively final.
    static String test() {
      if (f == null) {
        f = "Hello, world!";
      }
      return f;
    }
  }
}
