// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package switchpatternmatching;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EnumSwitchTest extends TestBase {

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public static String EXPECTED_OUTPUT = StringUtils.lines("null", "E1", "E2", "E3", "E4", "a C");

  @Test
  public void testJvm() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    CodeInspector inspector = new CodeInspector(ToolHelper.getClassFileForTestClass(Main.class));
    // javac generated an invokedynamic using bootstrap method
    // java.lang.runtime.SwitchBootstraps.typeSwitch.
    try {
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
    } catch (CompilationError e) {
      assertEquals("Could not parse code", e.getMessage());
      assertEquals(
          "Unsupported bootstrap static argument of type ConstantDynamic",
          e.getCause().getMessage());
    }

    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            parameters.getCfRuntime().isNewerThanOrEqual(CfVm.JDK21),
            r -> r.assertSuccessWithOutput(EXPECTED_OUTPUT),
            r -> r.assertFailureWithErrorThatThrows(UnsupportedClassVersionError.class));
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    assertThrows(
        CompilationFailedException.class,
        () -> testForD8().addInnerClasses(getClass()).setMinApi(parameters).compile());
  }

  @Test
  public void testR8() throws Exception {
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForR8(parameters.getBackend())
                .addInnerClasses(getClass())
                .setMinApi(parameters)
                .addKeepMainRule(Main.class)
                .compile());
  }

  sealed interface I permits E, C {}

  public enum E implements I {
    E1,
    E2,
    E3,
    E4
  }

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
    }
  }
}
