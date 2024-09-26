// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class B369670481 extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(ConcurrentModificationException.class);
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            parameters.isCfRuntime(),
            rr -> rr.assertFailureWithErrorThatThrows(ConcurrentModificationException.class),
            rr -> rr.assertSuccessWithOutputLines("2"));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            parameters.isCfRuntime(),
            rr -> rr.assertFailureWithErrorThatThrows(ConcurrentModificationException.class),
            rr -> rr.assertSuccessWithOutputLines("2"));
  }

  static class Main {

    public static void main(String[] strArr) {
      ArrayList<Integer> numbers = new ArrayList<>();
      numbers.add(10);
      numbers.add(20);
      numbers.add(40);
      Iterator<Integer> iterator = numbers.iterator();
      while (iterator.hasNext()) {
        if (iterator.next() == 40) {
          numbers.remove(2);
        }
      }
      System.out.println(numbers.size());
    }
  }
}
