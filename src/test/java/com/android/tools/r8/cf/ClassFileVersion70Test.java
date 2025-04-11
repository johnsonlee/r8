// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ClassFileVersion70Test extends TestBase {

  static final String EXPECTED = StringUtils.lines("Hello, world");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDefaultRuntimes()
        .enableApiLevelsForCf()
        .withApiLevel(AndroidApiLevel.B)
        .build();
  }

  public ClassFileVersion70Test(TestParameters parameters) {
    this.parameters = parameters;
  }

  // Update ASM once it has a release with v26 support.
  @Test(expected = CompilationFailedException.class)
  public void test() throws Exception {
    testForD8(parameters.getBackend())
        .addProgramClassFileData(transformer(TestClass.class).setVersion(CfVersion.V26).transform())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println("Hello, world");
    }
  }
}
