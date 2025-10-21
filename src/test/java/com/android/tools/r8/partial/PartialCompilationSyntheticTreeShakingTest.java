// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PartialCompilationSyntheticTreeShakingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    parameters.assumeCanUseR8Partial();
    testForR8Partial(parameters)
        .addR8IncludedClasses(Main.class)
        .addKeepMainRule(Main.class)
        .compile()
        .inspect(
            inspector -> {
              // The main() method has been optimized into being empty.
              MethodSubject mainMethod = inspector.clazz(Main.class).mainMethod();
              assertThat(mainMethod, isPresent());
              assertTrue(mainMethod.getMethod().getCode().isEmptyVoidMethod());
              // There should be no lambda class.
              assertEquals(1, inspector.allClasses().size());
            });
  }

  static class Main {

    public static void main(String[] args) {
      if (alwaysFalse()) {
        Runnable r = () -> System.out.println("Hello, world!");
        System.out.println(r);
      }
    }

    static boolean alwaysFalse() {
      return false;
    }
  }
}
