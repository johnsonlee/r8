// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import static org.junit.Assert.assertFalse;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.PrintStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class EmptyCollectionsTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public EmptyCollectionsTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .applyIf(
            parameters.isCfRuntime(), b -> b.addLibraryFiles(ToolHelper.getMostRecentAndroidJar()))
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("[]", "{}", "[]", "[]", "{}", "[]");
  }

  private void inspect(CodeInspector inspector) {
    inspector
        .streamInstructions()
        .forEach(ins -> assertFalse("Found:" + ins, ins.isInvokeStatic()));
  }

  static class TestClass {
    public static void main(String[] args) {
      PrintStream out = System.out;
      out.println(List.of());
      out.println(Map.of());
      out.println(Set.of());
      out.println(Collections.emptyList());
      out.println(Collections.emptyMap());
      out.println(Collections.emptySet());
    }
  }
}
