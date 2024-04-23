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
public class TypeSwitchTest extends TestBase {

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public static String EXPECTED_OUTPUT =
      StringUtils.lines(
          "null",
          "String",
          "Color: RED",
          "Record class: Point[i=0, j=0]",
          "Array of int, length = 0",
          "Other");

  @Test
  public void testJvm() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    CodeInspector inspector = new CodeInspector(ToolHelper.getClassFileForTestClass(Main.class));
    // javac generated an invokedynamic using bootstrap method argument of an arrya type (sort 9
    // is org.objectweb.asm.Type.ARRAY).
    try {
      inspector
          .clazz(Main.class)
          .uniqueMethodWithOriginalName("typeSwitch")
          .streamInstructions()
          .filter(InstructionSubject::isInvokeDynamic)
          .count();
    } catch (CompilationError e) {
      assertEquals("Could not parse code", e.getMessage());
      assertEquals("Type sort is not supported: 9", e.getCause().getMessage());
    }
    // javac generated an invokedynamic using bootstrap method
    // java.lang.runtime.SwitchBootstraps.typeSwitch.
    assertEquals(
        1,
        inspector
            .clazz(Main.class)
            .uniqueMethodWithOriginalName("typeSwitchNoArray")
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

  record Point(int i, int j) {}

  enum Color {
    RED,
    GREEN,
    BLUE;
  }

  static class Main {

    // No array version only for loading into code inspector.
    static void typeSwitchNoArray(Object obj) {
      switch (obj) {
        case null -> System.out.println("null");
        case String string -> System.out.println("String");
        case Color color -> System.out.println("Color: " + color);
        case Point point -> System.out.println("Record class: " + point);
        default -> System.out.println("Other");
      }
    }

    static void typeSwitch(Object obj) {
      switch (obj) {
        case null -> System.out.println("null");
        case String string -> System.out.println("String");
        case Color color -> System.out.println("Color: " + color);
        case Point point -> System.out.println("Record class: " + point);
        case int[] intArray -> System.out.println("Array of int, length = " + intArray.length);
        default -> System.out.println("Other");
      }
    }

    public static void main(String[] args) {
      typeSwitch(null);
      typeSwitch("s");
      typeSwitch(Color.RED);
      typeSwitch(new Point(0, 0));
      typeSwitch(new int[] {});
      typeSwitch(new Object());
    }
  }
}
