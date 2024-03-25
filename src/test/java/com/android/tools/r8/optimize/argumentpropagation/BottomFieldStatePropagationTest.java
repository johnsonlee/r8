// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation;

import com.android.tools.r8.KeepConstantArguments;
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
public class BottomFieldStatePropagationTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableConstantArgumentAnnotations()
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(NullPointerException.class);
  }

  static class Main {

    public static void main(String[] args) {
      Builder builder = alwaysFalse() ? new Builder(args.length) : null;
      test(builder);
    }

    static boolean alwaysFalse() {
      return false;
    }

    @KeepConstantArguments
    @NeverInline
    static void test(Builder builder) {
      // Argument propagation should prove that the field access is unreachable due to the field
      // value being bottom.
      unreachable(builder.f);
    }

    @KeepConstantArguments
    @NeverInline
    static void unreachable(int i) {
      System.out.println(i);
    }
  }

  static class Builder {

    int f;

    Builder(int f) {
      this.f = f;
    }
  }
}
