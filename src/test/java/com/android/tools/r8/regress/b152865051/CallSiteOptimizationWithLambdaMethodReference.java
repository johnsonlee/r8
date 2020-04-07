// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress.b152865051;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CallSiteOptimizationWithLambdaMethodReference extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public CallSiteOptimizationWithLambdaMethodReference(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws IOException, CompilationFailedException, ExecutionException {
    testForR8(parameters.getBackend())
        .addInnerClasses(CallSiteOptimizationWithLambdaMethodReference.class)
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            internalOptions -> {
              internalOptions.enableClassInlining = false;
            })
        .run(parameters.getRuntime(), Main.class)
        .disassemble()
        .assertSuccessWithOutputLines("NULL", "NOT NULL");
  }

  @FunctionalInterface
  public interface I {
    void action(Object a);
  }

  public static class Holder {

    private I i;

    Holder(I i) {
      this.i = i;
    }

    void callAction() {
      i.action(new Object());
    }
  }

  public static class Main {

    public static void main(String[] args) {
      ((I) Main::action).action(null);
      new Holder(Main::action).callAction();
    }

    private static void action(Object a) {
      System.out.println(a == null ? "NULL" : "NOT NULL");
    }
  }
}
