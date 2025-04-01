// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jdk24.switchpatternmatching;

import static com.android.tools.r8.desugar.switchpatternmatching.SwitchTestHelper.hasJdk21TypeSwitch;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.JdkClassFileProvider;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StringSwitchRegress382880986Test extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK24)
        .withDexRuntimes()
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  private static final String EXPECTED_OUTPUT = StringUtils.lines("1", "2", "3");

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();

    CodeInspector inspector =
        new CodeInspector(ToolHelper.getClassFileForTestClass(TestClass.class));
    assertTrue(
        hasJdk21TypeSwitch(inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("m")));

    testForJvm(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), TestClass.class, "hello", "goodbye", "")
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters.getBackend())
        .addInnerClassesAndStrippedOuter(getClass())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class, "hello", "goodbye", "")
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addInnerClassesAndStrippedOuter(getClass())
        .applyIf(
            parameters.isCfRuntime(),
            b -> b.addLibraryProvider(JdkClassFileProvider.fromSystemJdk()))
        .setMinApi(parameters)
        .addKeepMainRule(TestClass.class)
        .run(parameters.getRuntime(), TestClass.class, "hello", "goodbye", "")
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  static class TestClass {
    static final String hello = "hello";

    static void m(String s) {
      switch (s) {
        case hello -> System.out.println(1);
        case "goodbye" -> {
          System.out.println(2);
        }
        case null, default -> System.out.println(3);
      }
    }

    public static void main(String[] args) {
      m(args[0]);
      m(args[1]);
      m(args[2]);
    }
  }
}
