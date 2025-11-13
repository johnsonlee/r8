// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.lambdas;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.R8TestCompileResultBase;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LambdaMinimizeSyntheticNamesTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesAndAllApiLevels().build();
  }

  @Test
  public void testD8() throws Exception {
    TestCompileResult<?, ?> compileResult =
        testForD8(parameters)
            .addInnerClasses(getClass())
            .addOptionsModification(this::configure)
            .collectSyntheticItems()
            .release()
            .compile();
    compileResult.inspect(inspector -> inspect(inspector, compileResult.getSyntheticItems()));
  }

  @Test
  public void testR8() throws Exception {
    R8TestCompileResultBase<?> compileResult =
        testForR8(parameters)
            .addInnerClasses(getClass())
            .addKeepMainRule(Main.class)
            .addDontObfuscate()
            .addOptionsModification(this::configure)
            .collectSyntheticItems()
            .compile();
    compileResult.inspect(inspector -> inspect(inspector, compileResult.getSyntheticItems()));
  }

  private void configure(InternalOptions options) {
    options.desugarSpecificOptions().minimizeSyntheticNames = true;
  }

  private void inspect(CodeInspector inspector, SyntheticItemsTestUtils syntheticItems) {
    ClassSubject lambdaClass = inspector.clazz(syntheticItems.syntheticLambdaClass(Main.class, 0));
    assertThat(lambdaClass, isPresentAndNotRenamed());
  }

  static class Main {

    public static void main(String[] args) {
      Runnable runnable = () -> {};
      System.out.println(runnable);
    }
  }
}
