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
public class ArgumentInLowRegisterWithMoreThan16RegistersTest extends TestBase {

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
        .release()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              MethodSubject testMethodSubject =
                  inspector.clazz(Main.class).uniqueInstanceInitializer();
              assertThat(testMethodSubject, isPresent());
              assertEquals(
                  1,
                  testMethodSubject
                      .streamInstructions()
                      .filter(InstructionSubject::isMove)
                      .count());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithEmptyOutput();
  }

  static class Main {

    long a;
    long b;
    long c;
    long d;
    long e;
    long f;
    long g;
    long h;

    Main(long a, long b, long c, long d, long e, long f, long g, long h) {
      this.a = a;
      this.b = b;
      this.c = c;
      this.d = d;
      this.e = e;
      this.f = f;
      this.g = g;
      this.h = h;
    }

    public static void main(String[] args) {
      new Main(1, 2, 3, 4, 5, 6, 7, 8);
    }
  }
}
