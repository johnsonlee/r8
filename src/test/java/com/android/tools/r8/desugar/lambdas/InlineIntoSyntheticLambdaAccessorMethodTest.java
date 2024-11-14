// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.lambdas;

import static com.android.tools.r8.ir.desugar.LambdaClass.R8_LAMBDA_ACCESSOR_METHOD_PREFIX;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentIf;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InlineIntoSyntheticLambdaAccessorMethodTest extends TestBase {

  @Parameter(0)
  public boolean debug;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, debug: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withDexRuntimesAndAllApiLevels().build());
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addDontObfuscate()
        .addDontOptimize()
        .addDontShrink()
        .applyIf(debug, TestCompilerBuilder::debug)
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject mainClassSubject = inspector.clazz(Main.class);
              assertThat(mainClassSubject, isPresent());

              MethodSubject lambdaMethodSubject =
                  mainClassSubject.uniqueMethodWithFinalName("lambda$main$0");
              assertThat(lambdaMethodSubject, isPresentIf(debug));

              MethodSubject accessorMethodSubject =
                  mainClassSubject.uniqueMethodThatMatches(
                      method -> method.getFinalName().startsWith(R8_LAMBDA_ACCESSOR_METHOD_PREFIX));
              assertThat(accessorMethodSubject, isPresent());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithEmptyOutput();
  }

  static class Main {

    public static void main(String[] args) {
      new Thread(() -> System.out.println("Running!"));
    }
  }
}
