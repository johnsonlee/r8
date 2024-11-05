// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.regalloc;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.dex.code.DexMoveResult;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.MoreCollectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SpillToHighUnusedArgumentRegisterTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultDexRuntime().withMaximumApiLevel().build();
  }

  @Test
  public void testD8() throws Exception {
    testForD8()
        .addInnerClasses(getClass())
        .addOptionsModification(
            options -> options.getTestingOptions().enableRegisterAllocation8BitRefinement = true)
        .release()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              MethodSubject testMethodSubject =
                  inspector.clazz(Main.class).uniqueMethodWithOriginalName("test");
              assertThat(testMethodSubject, isPresent());

              DexCode code = testMethodSubject.getMethod().getCode().asDexCode();
              DexMoveResult moveResult =
                  testMethodSubject
                      .streamInstructions()
                      .filter(InstructionSubject::isMoveResult)
                      .collect(MoreCollectors.onlyElement())
                      .asDexInstruction()
                      .getInstruction();

              // TODO(b/375142715): The test no longer spills the `i` value. Look into if the test
              //  can be tweeked so that `i` is spilled, and validate that it is spilled to the
              //  unused argument register.
              assertTrue(
                  testMethodSubject
                      .streamInstructions()
                      .noneMatch(i -> i.isMoveFrom(moveResult.AA)));
            });
  }

  static class Main {

    static void test(long a, long b, long c, long d, long e, long f, long g, long h, int unused) {
      int i = getInt();
      forceIntoLowRegister(i, i);
      forceIntoLowRegister(a, a);
      forceIntoLowRegister(b, b);
      forceIntoLowRegister(c, c);
      forceIntoLowRegister(d, d);
      forceIntoLowRegister(e, e);
      forceIntoLowRegister(f, f);
      forceIntoLowRegister(g, g);
      forceIntoLowRegister(h, h);
      unconstrainedUse(i);
      forceIntoLowRegister(a, a);
      forceIntoLowRegister(b, b);
      forceIntoLowRegister(c, c);
      forceIntoLowRegister(d, d);
      forceIntoLowRegister(e, e);
      forceIntoLowRegister(f, f);
      forceIntoLowRegister(g, g);
      forceIntoLowRegister(h, h);
    }

    static int getInt() {
      return 0;
    }

    static void forceIntoLowRegister(int a, int b) {}

    static void forceIntoLowRegister(long a, long b) {}

    static void unconstrainedUse(int a) {}
  }
}
