// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package switchpatternmatching;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.JdkClassFileProvider;
import com.android.tools.r8.TestBase;
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
import switchpatternmatching.TypeSwitchTest.Main;

@RunWith(Parameterized.class)
public class StringSwitchTest extends TestBase {

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public static String EXPECTED_OUTPUT =
      StringUtils.lines(
          "null", "y or Y", "y or Y", "n or N", "n or N", "yes", "yes", "no", "no", "unknown");

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
            .uniqueMethodWithOriginalName("stringSwitch")
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
    testForD8()
        .addInnerClassesAndStrippedOuter(getClass())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    Assume.assumeTrue(
        parameters.isDexRuntime()
            || (parameters.isCfRuntime()
                && parameters.getCfRuntime().isNewerThanOrEqual(CfVm.JDK21)));
    testForR8(parameters.getBackend())
        .addInnerClassesAndStrippedOuter(getClass())
        .applyIf(
            parameters.isCfRuntime(),
            b -> b.addLibraryProvider(JdkClassFileProvider.fromSystemJdk()))
        .setMinApi(parameters)
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
