// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.workaround;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.util.Objects;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Regression test for b/348785664. */
@RunWith(Parameterized.class)
public class ExpectedToBeWithinCatchAllAfterInliningWithSoftVerificationErrorTest extends TestBase {

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
        .applyIf(!canUseJavaUtilObjects(parameters), builder -> builder.addDontWarn(Objects.class))
        // Disable desugaring to prevent backporting of Objects.isNull.
        .disableDesugaring()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  static class Main {

    public static void main(String[] args) {
      try {
        // After inlining of greet(), if the catch handler responsible for exiting the monitor is
        // not itself guarded by a catch-all, then verification fails due to the presence of the
        // non-catch-all handler.
        greet();
      } catch (IllegalArgumentException e) {
        e.printStackTrace();
      }

      // Verification error only happens when the method has a soft verification error.
      // We therefore call Objects.isNull, which is only defined from API level 24.
      if (args.length > 0) {
        System.out.println(Objects.isNull(args));
      }
    }

    static synchronized void greet() {
      System.out.println("Hello, world!");
    }
  }
}
