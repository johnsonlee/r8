// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.deadcode;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DeadObjectWithFinalizeTest extends TestBase {

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
        .assertSuccessWithOutputLines("passed fence");
  }

  static class Main {

    static final CountDownLatch fence = new CountDownLatch(1);

    public static void main(String[] args) throws Exception {
      new Object() {

        @Override
        protected void finalize() {
          fence.countDown();
        }
      };
      Runtime.getRuntime().gc();
      Runtime.getRuntime().runFinalization();
      if (fence.await(10, TimeUnit.SECONDS)) {
        System.out.println("passed fence");
      } else {
        System.out.println("fence await timed out");
      }
    }
  }
}
