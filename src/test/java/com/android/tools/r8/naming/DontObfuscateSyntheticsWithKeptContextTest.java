// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DontObfuscateSyntheticsWithKeptContextTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean enableRepackaging;

  @Parameters(name = "{0}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimesAndAllApiLevels().withPartialCompilation().build(),
        BooleanUtils.values());
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepAllClassesRule()
        .applyIf(enableRepackaging, b -> b.addKeepRules("-repackageclasses"))
        .compile()
        .inspectIf(
            !parameters.isRandomPartialCompilation(),
            inspector -> {
              // TODO(b/422530029): Add option to keep lambda name without fully disabling
              // obfuscation.
              ClassSubject lambdaClassSubject =
                  inspector.clazz(SyntheticItemsTestUtils.syntheticLambdaClass(Main.class, 0));
              assertThat(
                  lambdaClassSubject,
                  isPresentAndRenamed(parameters.getPartialCompilationTestParameters().isNone()));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  static class Main {

    public static void main(String[] args) {
      run(() -> System.out.println("Hello, world!"));
    }

    static void run(Runnable r) {
      r.run();
    }
  }
}
