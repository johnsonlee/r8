// Copyright (c) 2025 the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jdk24.switchpatternmatching;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.JdkClassFileProvider;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EnumSwitchOldSyntaxV2Test extends TestBase {

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

  public static String EXPECTED_OUTPUT = StringUtils.lines("null", "e11", "e22", "e33");

  @Test
  public void testJvm() throws Exception {
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
        .addKeepEnumsRule()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8Split() throws Exception {
    parameters.assumeR8TestParameters();
    R8TestCompileResult compile =
        testForR8(Backend.CF)
            .addInnerClassesAndStrippedOuter(getClass())
            .addLibraryProvider(JdkClassFileProvider.fromSystemJdk())
            .addKeepMainRule(Main.class)
            .addKeepEnumsRule()
            .compile();
    compile.inspect(i -> assertEquals(1, i.clazz(E.class).allFields().size()));
    // The enum is there with the $VALUES field but not each enum field.
    testForR8(parameters)
        .addProgramFiles(compile.writeToZip())
        .addKeepMainRule(Main.class)
        .addKeepEnumsRule()
        .applyIf(
            parameters.isCfRuntime(),
            b -> b.addLibraryProvider(JdkClassFileProvider.fromSystemJdk()))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  public enum E {
    E1,
    E2,
    E3
  }

  static class Main {

    static void enumSwitch(E e) {
      switch (e) {
        case E.E1:
          System.out.println("e11");
          break;
        case E.E2:
          System.out.println("e22");
          break;
        case E.E3:
          System.out.println("e33");
          break;
        case null:
          System.out.println("null");
          break;
      }
    }

    public static void main(String[] args) {
      try {
        enumSwitch(null);
      } catch (NullPointerException e) {
        System.out.println("caught npe");
      }
      for (E value : E.values()) {
        enumSwitch(value);
      }
    }
  }
}
