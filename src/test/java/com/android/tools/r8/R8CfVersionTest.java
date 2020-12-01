// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class R8CfVersionTest extends TestBase {

  private final CfVersion targetVersion = CfVersion.V1_8;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public R8CfVersionTest(TestParameters parameters) {}

  @Test
  public void testCfVersionR8() throws IOException {
    CodeInspector inspector = new CodeInspector(ToolHelper.R8_WITH_DEPS_JAR);
    inspector.forAllClasses(
        clazz -> {
          assertTrue(
              clazz
                  .getDexProgramClass()
                  .getInitialClassFileVersion()
                  .isLessThanOrEqualTo(targetVersion));
        });
  }

  @Test
  public void testCfVersionR8Lib() throws IOException {
    CodeInspector inspector = new CodeInspector(ToolHelper.R8LIB_JAR);
    inspector.forAllClasses(
        clazz -> {
          assertTrue(
              clazz
                  .getDexProgramClass()
                  .getInitialClassFileVersion()
                  .isLessThanOrEqualTo(targetVersion));
        });
  }
}
