// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jdk24.switchpatternmatching;

import static com.android.tools.r8.desugar.switchpatternmatching.SwitchTestHelper.hasJdk21TypeSwitch;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

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

// This is a copy of the same test from JDK-21. The reason for the copy is that from JDK-23 the
// code generation for pattern matching switch changed (the bootstrap method signature used in the
// invokedynamic changed).
@RunWith(Parameterized.class)
public class StringSwitchTest extends TestBase {

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK24)
        .withDexRuntimes()
        .withAllApiLevelsAlsoForCf()
        .withPartialCompilation()
        .build();
  }

  public static String EXPECTED_OUTPUT =
      StringUtils.lines(
          "null", "y or Y", "y or Y", "n or N", "n or N", "yes", "yes", "no", "no", "unknown");

  @Test
  public void testJvm() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    CodeInspector inspector = new CodeInspector(ToolHelper.getClassFileForTestClass(Main.class));
    assertTrue(
        hasJdk21TypeSwitch(
            inspector.clazz(Main.class).uniqueMethodWithOriginalName("stringSwitch")));

    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .applyIf(
            parameters.isCfRuntime(),
            b -> b.addLibraryProvider(JdkClassFileProvider.fromSystemJdk()))
        .addKeepMainRule(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  static class Main {

    static void stringSwitch(String string) {
      switch (string) {
        case null -> {
          System.out.println("null");
        }
        case String s when s.equalsIgnoreCase("YES") -> {
          System.out.println("yes");
        }
        case "y", "Y" -> {
          System.out.println("y or Y");
        }
        case String s when s.equalsIgnoreCase("NO") -> {
          System.out.println("no");
        }
        case "n", "N" -> {
          System.out.println("n or N");
        }
        case String s -> {
          System.out.println("unknown");
        }
      }
    }

    public static void main(String[] args) {
      stringSwitch(null);
      stringSwitch("y");
      stringSwitch("Y");
      stringSwitch("n");
      stringSwitch("N");
      stringSwitch("yes");
      stringSwitch("YES");
      stringSwitch("no");
      stringSwitch("NO");
      stringSwitch("?");
    }
  }
}
