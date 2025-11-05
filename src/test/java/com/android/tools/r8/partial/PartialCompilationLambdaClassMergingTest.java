// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PartialCompilationLambdaClassMergingTest extends TestBase {

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
        .addR8IncludedClasses(Main.class, I.class)
        .addR8IncludedClasses(false, NeverInline.class)
        .addKeepClassAndMembersRules(Main.class)
        .collectSyntheticItems()
        .compile()
        .inspectWithSyntheticItems(
            (inspector, syntheticItems) -> {
              // The output has three classes: Main, I and a lambda.
              assertEquals(3, inspector.allClasses().size());
              // The output has a single synthetic lambda due to class merging.
              assertEquals(
                  1,
                  inspector.allClasses().stream()
                      .filter(
                          clazz -> syntheticItems.isExternalLambda(clazz.getOriginalReference()))
                      .count());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  static class Main {

    public static void main(String[] args) {
      call(() -> System.out.print("Hello"));
      call(() -> System.out.println(", world!"));
    }

    static void call(I i) {
      i.f();
    }
  }

  interface I {

    void f();
  }
}
