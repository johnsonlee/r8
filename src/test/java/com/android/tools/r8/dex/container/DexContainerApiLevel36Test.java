// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.container;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DexContainerApiLevel36Test extends DexContainerFormatTestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void testD8() throws Exception {
    Path output =
        testForD8(Backend.DEX)
            .addProgramClasses(TestClass.class)
            .setMinApi(AndroidApiLevel.BAKLAVA)
            .compile()
            .writeToZip();
    validateDex(output, 1, AndroidApiLevel.V.getDexVersion());
  }

  @Test
  public void testR8() throws Exception {
    Path output =
        testForR8(Backend.DEX)
            .addProgramClasses(TestClass.class)
            .setMinApi(AndroidApiLevel.BAKLAVA)
            .addKeepAllClassesRule()
            .compile()
            .writeToZip();
    validateDex(output, 1, AndroidApiLevel.V.getDexVersion());
  }

  static class TestClass {}
}
