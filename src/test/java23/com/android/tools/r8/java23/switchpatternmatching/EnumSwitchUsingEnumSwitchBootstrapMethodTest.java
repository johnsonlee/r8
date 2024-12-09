// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.java23.switchpatternmatching;

import static com.android.tools.r8.desugar.switchpatternmatching.SwitchTestHelper.hasJdk21EnumSwitch;
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
public class EnumSwitchUsingEnumSwitchBootstrapMethodTest extends TestBase {

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public static String EXPECTED_OUTPUT =
      StringUtils.lines(
          "null",
          "Spades or Piques",
          "Hearts or C\u0153ur",
          "Diamonds or Carreaux",
          "Clubs or Trefles",
          "Trumps or Atouts",
          "The Fool or L'Excuse");

  public static String EXPECTED_OUTPUT_ASCII =
      StringUtils.lines(
          "null",
          "Spades or Piques",
          "Hearts or Coeur",
          "Diamonds or Carreaux",
          "Clubs or Trefles",
          "Trumps or Atouts",
          "The Fool or L'Excuse");

  @Test
  public void testJvm() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    CodeInspector inspector = new CodeInspector(ToolHelper.getClassFileForTestClass(Main.class));
    assertTrue(
        hasJdk21EnumSwitch(inspector.clazz(Main.class).uniqueMethodWithOriginalName("enumSwitch")));

    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            parameters.getCfRuntime().isNewerThanOrEqual(CfVm.JDK23),
            r -> r.assertSuccessWithOutput(EXPECTED_OUTPUT),
            r -> r.assertFailureWithErrorThatThrows(UnsupportedClassVersionError.class));
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addInnerClassesAndStrippedOuter(getClass())
        .setMinApi(parameters)
        // Windows does not like the non ascii characters.
        .run(parameters.getRuntime(), Main.class, ToolHelper.isWindows() ? "ascii" : "")
        .assertSuccessWithOutput(ToolHelper.isWindows() ? EXPECTED_OUTPUT_ASCII : EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    Assume.assumeTrue(
        parameters.isDexRuntime()
            || (parameters.isCfRuntime()
                && parameters.getCfRuntime().isNewerThanOrEqual(CfVm.JDK23)));
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

  public enum Tarot {
    SPADE,
    HEART,
    DIAMOND,
    CLUB,
    TRUMP,
    EXCUSE
  }

  public static class Main {
    static boolean ascii = false;

    public static void main(String[] args) {
      ascii = args.length > 0 && args[0].equals("ascii");
      enumSwitch(null);
      enumSwitch(Tarot.SPADE);
      enumSwitch(Tarot.HEART);
      enumSwitch(Tarot.DIAMOND);
      enumSwitch(Tarot.CLUB);
      enumSwitch(Tarot.TRUMP);
      enumSwitch(Tarot.EXCUSE);
    }

    static void enumSwitch(Tarot t1) {
      switch (t1) {
        case null -> System.out.println("null");
        case SPADE -> System.out.println("Spades or Piques");
        case HEART -> System.out.println(ascii ? "Hearts or Coeur" : "Hearts or C\u0153ur");
        case Tarot t when t == Tarot.DIAMOND -> System.out.println("Diamonds or Carreaux");
        case Tarot t when t == Tarot.CLUB -> System.out.println("Clubs or Trefles");
        case Tarot t when t == Tarot.TRUMP -> System.out.println("Trumps or Atouts");
        case Tarot t -> System.out.println("The Fool or L'Excuse");
      }
    }
  }
}
