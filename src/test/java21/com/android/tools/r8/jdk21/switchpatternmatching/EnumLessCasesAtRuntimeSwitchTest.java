// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jdk21.switchpatternmatching;

import static com.android.tools.r8.desugar.switchpatternmatching.SwitchTestHelper.hasJdk21EnumSwitch;
import static com.android.tools.r8.desugar.switchpatternmatching.SwitchTestHelper.hasJdk21TypeSwitch;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.JdkClassFileProvider;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestBuilder;
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

// This test is copied into later JDK tests (currently JDK-23). The reason for the copy is that
// from JDK-23 the code generation changed. Please update the copy as well if updating this test.
@RunWith(Parameterized.class)
public class EnumLessCasesAtRuntimeSwitchTest extends TestBase {

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK21)
        .withDexRuntimes()
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  public static String EXPECTED_OUTPUT =
      StringUtils.lines("TYPE", "null", "E1", "E3", "E5", "a C", "ENUM", "null", "1", "3", "0");

  @Test
  public void testJvm() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    CodeInspector inspector = new CodeInspector(ToolHelper.getClassFileForTestClass(Main.class));
    assertTrue(
        hasJdk21EnumSwitch(inspector.clazz(Main.class).uniqueMethodWithOriginalName("enumSwitch")));
    assertTrue(
        hasJdk21TypeSwitch(inspector.clazz(Main.class).uniqueMethodWithOriginalName("typeSwitch")));

    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .apply(this::addModifiedProgramClasses)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  private <T extends TestBuilder<?, T>> void addModifiedProgramClasses(
      TestBuilder<?, T> testBuilder) throws Exception {
    testBuilder
        .addStrippedOuter(getClass())
        .addProgramClasses(FakeI.class, C.class, I.class, Main.class)
        .addProgramClassFileData(
            transformer(CompileTimeE.class)
                .setImplements(FakeI.class)
                .setClassDescriptor(RuntimeE.class.descriptorString())
                .transform())
        .addProgramClassFileData(
            transformer(RuntimeE.class)
                .setImplements(I.class)
                .setClassDescriptor(CompileTimeE.class.descriptorString())
                .transform());
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .apply(this::addModifiedProgramClasses)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .apply(this::addModifiedProgramClasses)
        .applyIf(
            parameters.isCfRuntime(),
            b -> b.addLibraryProvider(JdkClassFileProvider.fromSystemJdk()))
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .addKeepEnumsRule()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  sealed interface I permits CompileTimeE, C {}

  public enum CompileTimeE implements I {
    E1,
    E2, // Case missing at runtime.
    E3,
    E4, // Case missing at runtime.
    E5
  }

  interface FakeI {}

  public enum RuntimeE implements FakeI {
    E1,
    E3,
    E5
  }

  static final class C implements I {}

  static class Main {

    static void typeSwitch(I i) {
      switch (i) {
        case CompileTimeE.E1 -> {
          System.out.println("E1");
        }
        case CompileTimeE.E2 -> { // Case missing at runtime.
          System.out.println("E2");
        }
        case CompileTimeE.E3 -> {
          System.out.println("E3");
        }
        case CompileTimeE.E4 -> { // Case missing at runtime.
          System.out.println("E4");
        }
        case CompileTimeE.E5 -> {
          System.out.println("E5");
        }
        case C c -> {
          System.out.println("a C");
        }
      }
    }

    static void enumSwitch(CompileTimeE e) {
      switch (e) {
        case null -> System.out.println("null");
        case CompileTimeE.E1 -> System.out.println("1");
        case CompileTimeE.E2 -> System.out.println("2"); // Case missing at runtime.
        case CompileTimeE t when t == CompileTimeE.E3 -> System.out.println("3");
        case CompileTimeE t when t.name().equals("E4") ->
            System.out.println("4"); // Case missing at runtime.
        case CompileTimeE t -> System.out.println("0");
      }
    }

    public static void main(String[] args) {
      System.out.println("TYPE");
      try {
        typeSwitch(null);
      } catch (NullPointerException e) {
        System.out.println("null");
      }
      typeSwitch(CompileTimeE.E1);
      typeSwitch(CompileTimeE.E3);
      typeSwitch(CompileTimeE.E5);
      typeSwitch(new C());

      System.out.println("ENUM");
      enumSwitch(null);
      enumSwitch(CompileTimeE.E1);
      enumSwitch(CompileTimeE.E3);
      enumSwitch(CompileTimeE.E5);
    }
  }
}
