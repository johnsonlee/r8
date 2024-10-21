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
public class ArgumentIn4BitRegisterTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesAndAllApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForD8()
        .addInnerClasses(getClass())
        .release()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              MethodSubject testMethodSubject =
                  inspector.clazz(Main.class).uniqueMethodWithOriginalName("test");
              assertThat(testMethodSubject, isPresent());
              // TODO(b/374266460): Should be 0.
              assertEquals(
                  1,
                  testMethodSubject
                      .streamInstructions()
                      .filter(InstructionSubject::isMove)
                      .count());
            });
  }

  static class Main {

    public static void test(
        Object a, long b, long c, long d, long e, long f, long g, long h, long i) {
      Main main = (Main) a;
      // Keep all argument alive.
      use(b);
      use(c);
      use(d);
      use(e);
      use(f);
      use(h);
      use(i);
    }

    private static void use(long a) {}
  }
}
