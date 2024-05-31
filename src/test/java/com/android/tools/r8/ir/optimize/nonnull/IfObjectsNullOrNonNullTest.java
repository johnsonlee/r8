// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.nonnull;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.Objects;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IfObjectsNullOrNonNullTest extends TestBase {

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
        .assertSuccessWithOutputLines("false", "true", "true", "false");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              MethodSubject mainMethodSubject = inspector.clazz(Main.class).mainMethod();
              assertThat(mainMethodSubject, isPresent());
              assertTrue(
                  mainMethodSubject
                      .streamInstructions()
                      .noneMatch(InstructionSubject::isInvokeStatic));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("false", "true", "true", "false");
  }

  static class Main {

    public static void main(String[] args) {
      boolean alwaysTrue = args.length == 0;
      Object nonNullObject = alwaysTrue ? new Object() : null;
      Object nullObject = alwaysTrue ? null : new Object();
      if (Objects.isNull(nonNullObject)) {
        System.out.println("true");
      } else {
        System.out.println("false");
      }
      if (Objects.isNull(nullObject)) {
        System.out.println("true");
      } else {
        System.out.println("false");
      }
      if (Objects.nonNull(nonNullObject)) {
        System.out.println("true");
      } else {
        System.out.println("false");
      }
      if (Objects.nonNull(nullObject)) {
        System.out.println("true");
      } else {
        System.out.println("false");
      }
    }
  }
}
