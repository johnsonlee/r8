// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package switchpatternmatching;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static switchpatternmatching.SwitchTestHelper.desugarMatchException;
import static switchpatternmatching.SwitchTestHelper.hasJdk21EnumSwitch;
import static switchpatternmatching.SwitchTestHelper.hasJdk21TypeSwitch;
import static switchpatternmatching.SwitchTestHelper.matchException;

import com.android.tools.r8.JdkClassFileProvider;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EnumMoreCasesAtRuntimeSwitchTest extends TestBase {

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public static String EXPECTED_OUTPUT =
      StringUtils.lines(
          "TYPE",
          "null",
          "E1",
          "class %s",
          "E3",
          "class %s",
          "E5",
          "a C",
          "ENUM",
          "null",
          "1",
          "0",
          "3",
          "4",
          "0");

  public static String UNEXPECTED_OUTPUT_R8_DEX =
      StringUtils.lines(
          "TYPE",
          "null",
          "E1",
          "class %s",
          "E3",
          "class %s",
          "E5",
          "a C",
          "ENUM",
          "null",
          "1",
          "0",
          "3",
          "0", // This is the difference from the EXPECTED_OUTPUT.
          "0");

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
        .applyIf(
            parameters.getCfRuntime().isNewerThanOrEqual(CfVm.JDK21),
            r ->
                r.assertSuccessWithOutput(
                    String.format(EXPECTED_OUTPUT, matchException(), matchException())),
            r -> r.assertFailureWithErrorThatThrows(UnsupportedClassVersionError.class));
  }

  private <T extends TestBuilder<?, T>> void addModifiedProgramClasses(
      TestBuilder<?, T> testBuilder) throws Exception {
    testBuilder
        .addStrippedOuter(getClass())
        .addProgramClasses(FakeI.class, C.class, I.class)
        .addProgramClassFileData(
            transformer(CompileTimeE.class)
                .setImplements(FakeI.class)
                .setClassDescriptor(RuntimeE.class.descriptorString())
                .transform())
        .addProgramClassFileData(
            transformer(RuntimeE.class)
                .setImplements(I.class)
                .setClassDescriptor(CompileTimeE.class.descriptorString())
                .transform())
        .addProgramClassFileData(
            transformer(Main.class)
                .transformFieldInsnInMethod(
                    "getE2",
                    (opcode, owner, name, descriptor, visitor) -> {
                      visitor.visitFieldInsn(opcode, owner, "E2", descriptor);
                    })
                .transformFieldInsnInMethod(
                    "getE4",
                    (opcode, owner, name, descriptor, visitor) -> {
                      visitor.visitFieldInsn(opcode, owner, "E4", descriptor);
                    })
                .transform());
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .apply(this::addModifiedProgramClasses)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(
            String.format(EXPECTED_OUTPUT, desugarMatchException(), desugarMatchException()));
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    Assume.assumeTrue(
        parameters.isDexRuntime()
            || (parameters.isCfRuntime()
                && parameters.getCfRuntime().isNewerThanOrEqual(CfVm.JDK21)));
    testForR8(parameters.getBackend())
        .apply(this::addModifiedProgramClasses)
        .applyIf(
            parameters.isCfRuntime(),
            b -> b.addLibraryProvider(JdkClassFileProvider.fromSystemJdk()))
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            parameters.isDexRuntime(),
            // TODO(b/381825147): Should same output.
            r ->
                r.assertSuccessWithOutput(
                    String.format(
                        UNEXPECTED_OUTPUT_R8_DEX,
                        matchException(parameters),
                        matchException(parameters))),
            r ->
                r.assertSuccessWithOutput(
                    String.format(
                        EXPECTED_OUTPUT, matchException(parameters), matchException(parameters))));
  }

  sealed interface I permits CompileTimeE, C {}

  public enum RuntimeE implements FakeI {
    E1,
    E2, // Case present at runtime.
    E3,
    E4, // Case present at runtime.
    E5
  }

  interface FakeI {}

  public enum CompileTimeE implements I {
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
        case CompileTimeE.E3 -> {
          System.out.println("E3");
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
        case CompileTimeE t when t == CompileTimeE.E3 -> System.out.println("3");
        case CompileTimeE t when t.name().equals("E4") ->
            System.out.println("4"); // Case present at runtime.
        case CompileTimeE t -> System.out.println("0");
      }
    }

    public static CompileTimeE getE2() {
      // Replaced by RuntimeE.E2;
      return CompileTimeE.E1;
    }

    public static CompileTimeE getE4() {
      // Replaced by RuntimeE.E4;
      return CompileTimeE.E1;
    }

    public static void main(String[] args) {
      System.out.println("TYPE");
      try {
        typeSwitch(null);
      } catch (NullPointerException e) {
        System.out.println("null");
      }
      typeSwitch(CompileTimeE.E1);
      try {
        typeSwitch(getE2());
      } catch (Exception e) {
        System.out.println(e.getClass().toString());
      }
      typeSwitch(CompileTimeE.E3);
      try {
        typeSwitch(getE4());
      } catch (Exception e) {
        System.out.println(e.getClass().toString());
      }
      typeSwitch(CompileTimeE.E5);
      typeSwitch(new C());

      System.out.println("ENUM");
      enumSwitch(null);
      enumSwitch(CompileTimeE.E1);
      enumSwitch(getE2());
      enumSwitch(CompileTimeE.E3);
      enumSwitch(getE4());
      enumSwitch(CompileTimeE.E5);
    }
  }
}
