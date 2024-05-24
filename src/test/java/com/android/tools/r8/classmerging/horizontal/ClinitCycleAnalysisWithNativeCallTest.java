// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.AssertUtils.assertFailsCompilation;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ClinitCycleAnalysisWithNativeCallTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    // TODO(b/341537881): Account for the native call.
    assertFailsCompilation(
        () ->
            testForR8(parameters.getBackend())
                .addInnerClasses(getClass())
                .addKeepMainRule(Main.class)
                .setMinApi(parameters)
                .compile());
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println(System.currentTimeMillis() > 0 ? new B() : new C());
    }
  }

  abstract static class A {

    static {
      m();
    }

    static native void m();
  }

  static class B extends A {}

  static class C extends A {}
}
