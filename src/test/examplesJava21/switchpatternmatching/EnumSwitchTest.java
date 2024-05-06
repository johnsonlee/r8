// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package switchpatternmatching;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import switchpatternmatching.StringSwitchTest.Main;

@RunWith(Parameterized.class)
public class EnumSwitchTest extends TestBase {

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public static String EXPECTED_OUTPUT =
      StringUtils.lines("null", "E1", "E2", "E3", "E4", "a C", "class %s");

  @Test
  public void testJvm() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    CodeInspector inspector = new CodeInspector(ToolHelper.getClassFileForTestClass(Main.class));
    // javac generated an invokedynamic using bootstrap method
    // java.lang.runtime.SwitchBootstraps.typeSwitch.
    assertEquals(
        1,
        inspector
            .clazz(Main.class)
            .uniqueMethodWithOriginalName("enumSwitch")
            .streamInstructions()
            .filter(InstructionSubject::isInvokeDynamic)
            .map(
                instruction ->
                    instruction
                        .asCfInstruction()
                        .getInstruction()
                        .asInvokeDynamic()
                        .getCallSite()
                        .getBootstrapMethod()
                        .member
                        .asDexMethod())
            .filter(
                method ->
                    method
                        .getHolderType()
                        .toString()
                        .contains("java.lang.runtime.SwitchBootstraps"))
            .filter(method -> method.toString().contains("typeSwitch"))
            .count());

    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .apply(this::addModifiedProgramClasses)
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            parameters.getCfRuntime().isNewerThanOrEqual(CfVm.JDK21),
            r ->
                r.assertSuccessWithOutput(
                    String.format(EXPECTED_OUTPUT, "java.lang.MatchException")),
            r -> r.assertFailureWithErrorThatThrows(UnsupportedClassVersionError.class));
  }

  private <T extends TestBuilder<?, T>> void addModifiedProgramClasses(
      TestBuilder<?, T> testBuilder) throws Exception {
    testBuilder
        .addStrippedOuter(getClass())
        .addProgramClasses(FakeI.class, E.class, C.class)
        .addProgramClassFileData(
            transformer(I.class)
                .setPermittedSubclasses(I.class, E.class, C.class, D.class)
                .transform())
        .addProgramClassFileData(transformer(D.class).setImplements(I.class).transform())
        .addProgramClassFileData(
            transformer(Main.class)
                .transformTypeInsnInMethod(
                    "getD",
                    (opcode, type, visitor) ->
                        visitor.visitTypeInsn(opcode, "switchpatternmatching/EnumSwitchTest$D"))
                .transformMethodInsnInMethod(
                    "getD",
                    (opcode, owner, name, descriptor, isInterface, visitor) -> {
                      assert name.equals("<init>");
                      visitor.visitMethodInsn(
                          opcode,
                          "switchpatternmatching/EnumSwitchTest$D",
                          name,
                          descriptor,
                          isInterface);
                    })
                .transform());
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .apply(this::addModifiedProgramClasses)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(String.format(EXPECTED_OUTPUT, "java.lang.RuntimeException"));
  }

  @Test
  public void testR8() throws Exception {
    Assume.assumeTrue("For Cf we should compile with Jdk 21 library", parameters.isDexRuntime());
    testForR8(parameters.getBackend())
        .apply(this::addModifiedProgramClasses)
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(String.format(EXPECTED_OUTPUT, "java.lang.RuntimeException"));
  }

  // D is added to the list of permitted subclasses to reproduce the MatchException.
  sealed interface I permits E, C {}

  interface FakeI {}

  public enum E implements I {
    E1,
    E2,
    E3,
    E4
  }

  // Replaced with I.
  static final class D implements FakeI {}

  static final class C implements I {}

  static class Main {

    static void enumSwitch(I i) {
      switch (i) {
        case E.E1 -> {
          System.out.println("E1");
        }
        case E.E2 -> {
          System.out.println("E2");
        }
        case E.E3 -> {
          System.out.println("E3");
        }
        case E.E4 -> {
          System.out.println("E4");
        }
        case C c -> {
          System.out.println("a C");
        }
      }
    }

    public static void main(String[] args) {
      try {
        enumSwitch(null);
      } catch (NullPointerException e) {
        System.out.println("null");
      }
      enumSwitch(E.E1);
      enumSwitch(E.E2);
      enumSwitch(E.E3);
      enumSwitch(E.E4);
      enumSwitch(new C());
      try {
        enumSwitch(getD());
      } catch (Throwable t) {
        System.out.println(t.getClass());
      }
    }

    public static I getD() {
      // Replaced by new D();
      return new C();
    }
  }
}
