// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.java23.switchpatternmatching;

import static com.android.tools.r8.desugar.switchpatternmatching.SwitchTestHelper.hasJdk21TypeSwitch;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.JdkClassFileProvider;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

// This is a copy of the same test from JDK-21. The reason for the copy is that from JDK-23 the
// code generation for pattern matching switch changed (the bootstrap method signature used in the
// invokedynamic changed).
@RunWith(Parameterized.class)
public class TypeSwitchMissingClassTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public ClassHolder present;

  @Parameters(name = "{0}, {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build(),
        List.of(new ClassHolder(C.class), new ClassHolder(Color.class)));
  }

  // ClassHolder allows to correctly print parameters in the IntelliJ test IDE with {1}.
  private static class ClassHolder {
    public final Class<?> clazz;

    private ClassHolder(Class<?> clazz) {
      this.clazz = clazz;
    }

    @Override
    public String toString() {
      return clazz.getSimpleName();
    }
  }

  public static String EXPECTED_OUTPUT =
      StringUtils.lines("null", "String", "Array of int, length = 0", "Other");

  @Test
  public void testJvm() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    CodeInspector inspector = new CodeInspector(ToolHelper.getClassFileForTestClass(Main.class));
    assertTrue(
        hasJdk21TypeSwitch(inspector.clazz(Main.class).uniqueMethodWithOriginalName("typeSwitch")));

    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .apply(this::addModifiedProgramClasses)
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            parameters.getCfRuntime().isNewerThanOrEqual(CfVm.JDK23),
            this::assertResult,
            r -> r.assertFailureWithErrorThatThrows(UnsupportedClassVersionError.class));
  }

  private void assertResult(TestRunResult<?> r) {
    if (present.clazz.equals(C.class)) {
      r.assertSuccessWithOutput(EXPECTED_OUTPUT);
    } else {
      r.assertFailureWithErrorThatThrows(NoClassDefFoundError.class);
    }
  }

  private <T extends TestBuilder<?, T>> void addModifiedProgramClasses(
      TestBuilder<?, T> testBuilder) throws Exception {
    testBuilder
        .addProgramClassFileData(transformer(Main.class).clearNest().transform())
        .addProgramClassFileData(transformer(present.clazz).clearNest().transform());
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .apply(this::addModifiedProgramClasses)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .apply(this::assertResult);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    Assume.assumeTrue(
        parameters.isDexRuntime()
            || (parameters.isCfRuntime()
                && parameters.getCfRuntime().isNewerThanOrEqual(CfVm.JDK23)));
    testForR8(parameters.getBackend())
        .apply(this::addModifiedProgramClasses)
        .applyIf(
            parameters.isCfRuntime(),
            b -> b.addLibraryProvider(JdkClassFileProvider.fromSystemJdk()))
        .addIgnoreWarnings(present.clazz.equals(Color.class))
        .allowDiagnosticWarningMessages(present.clazz.equals(Color.class))
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .apply(this::assertResult);
  }

  // Enum will be missing at runtime.
  enum Color {
    RED,
    GREEN,
    BLUE;
  }

  // Class will be missing at runtime.
  static class C {

    @Override
    public String toString() {
      return "CCC";
    }
  }

  static class Main {

    static void typeSwitch(Object obj) {
      switch (obj) {
        case null -> System.out.println("null");
        case Color.RED -> System.out.println("RED!!!");
        case Color.BLUE -> System.out.println("BLUE!!!");
        case Color.GREEN -> System.out.println("GREEN!!!");
        case String string -> System.out.println("String");
        case C c -> System.out.println(c.toString() + "!!!");
        case int[] intArray -> System.out.println("Array of int, length = " + intArray.length);
        default -> System.out.println("Other");
      }
    }

    public static void main(String[] args) {
      typeSwitch(null);
      typeSwitch("s");
      typeSwitch(new int[] {});
      typeSwitch(new Object());
    }
  }
}
