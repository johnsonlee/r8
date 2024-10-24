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
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvokeRangeForConsecutiveArgumentsTest extends TestBase {

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
              assertTrue(
                  testMethodSubject
                      .streamInstructions()
                      .filter(InstructionSubject::isInvokeMethod)
                      .allMatch(InstructionSubject::isInvokeRange));
              assertTrue(
                  testMethodSubject.streamInstructions().noneMatch(InstructionSubject::isMove));
            });
  }

  static class Main {

    void test(long a, long b, long c, long d, long e, long f, long g, long h) {
      test(a, b, c, d, e, f, g, h);
      testPartial(g, h);
    }

    static void testPartial(long a, long b) {}
  }
}
