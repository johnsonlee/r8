// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.regalloc;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RedundantSpillingBeforeInvokeRangeTest extends TestBase {

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
            options -> options.getTestingOptions().enableRegisterHintsForBlockedRegisters = true)
        .release()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              MethodSubject testMethodSubject =
                  inspector.clazz(Main.class).uniqueMethodWithOriginalName("test");
              assertThat(testMethodSubject, isPresent());
              assertEquals(
                  10,
                  testMethodSubject
                      .streamInstructions()
                      .filter(InstructionSubject::isMove)
                      .count());
            })
        .runDex2Oat(parameters.getRuntime())
        .assertNoVerificationErrors();
  }

  static class Main {

    Main(long a, long b, long c, long d, long e, long f, long g, long h) {}

    Main test(long a, long b, long c, long d, long e, long f, long g, long h) {
      forceIntoLowRegister(a, a);
      forceIntoLowRegister(b, b);
      forceIntoLowRegister(c, c);
      forceIntoLowRegister(d, d);
      forceIntoLowRegister(e, e);
      forceIntoLowRegister(f, f);
      forceIntoLowRegister(g, g);
      forceIntoLowRegister(h, h);
      return new Main(a, b, c, d, e, f, g, h);
    }

    static void forceIntoLowRegister(long a, long b) {}
  }
}
