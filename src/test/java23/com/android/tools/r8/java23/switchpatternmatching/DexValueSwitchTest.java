// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.java23.switchpatternmatching;

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

@RunWith(Parameterized.class)
public class DexValueSwitchTest extends TestBase {

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK23)
        .withDexRuntimes()
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  public static String EXPECTED_OUTPUT =
      StringUtils.lines(
          "null", "42", "positif", "negatif", "null", "c", "upper", "lower", "null", "42",
          "positif", "negatif", "null", "42", "positif", "negatif");

  @Test
  public void testJvm() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    CodeInspector inspector = new CodeInspector(ToolHelper.getClassFileForTestClass(Main.class));
    assertTrue(
        hasJdk21TypeSwitch(inspector.clazz(Main.class).uniqueMethodWithOriginalName("intSwitch")));

    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addInnerClassesAndStrippedOuter(getClass())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
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

    // static void booleanSwitch(Boolean b) {
    //   switch (b) {
    //     case null -> {
    //       System.out.println("null");
    //     }
    //     case true -> {
    //       System.out.println("true");
    //     }
    //     default -> {
    //       System.out.println("false");
    //     }
    //   }
    // }
    //
    // static void doubleSwitch(Double d) {
    //   switch (d) {
    //     case null -> {
    //       System.out.println("null");
    //     }
    //     case 42.0 -> {
    //       System.out.println("42");
    //     }
    //     case Double f2 when f2 > 0 -> {
    //       System.out.println("positif");
    //     }
    //     default -> {
    //       System.out.println("negatif");
    //     }
    //   }
    // }
    //
    // static void floatSwitch(Float f) {
    //   switch (f) {
    //     case null -> {
    //       System.out.println("null");
    //     }
    //     case 42.0f -> {
    //       System.out.println("42");
    //     }
    //     case Float f2 when f2 > 0 -> {
    //       System.out.println("positif");
    //     }
    //     default -> {
    //       System.out.println("negatif");
    //     }
    //   }
    // }
    //
    // static void longSwitch(Long l) {
    //   switch (l) {
    //     case null -> {
    //       System.out.println("null");
    //     }
    //     case 42L -> {
    //       System.out.println("42");
    //     }
    //     case Long i2 when i2 > 0 -> {
    //       System.out.println("positif");
    //     }
    //     default -> {
    //       System.out.println("negatif");
    //     }
    //   }
    // }

    static void intSwitch(Integer i) {
      switch (i) {
        case null -> {
          System.out.println("null");
        }
        case 42 -> {
          System.out.println("42");
        }
        case Integer i2 when i2 > 0 -> {
          System.out.println("positif");
        }
        default -> {
          System.out.println("negatif");
        }
      }
    }

    static void shortSwitch(Short s) {
      switch (s) {
        case null -> {
          System.out.println("null");
        }
        case 42 -> {
          System.out.println("42");
        }
        case Short s2 when s2 > 0 -> {
          System.out.println("positif");
        }
        default -> {
          System.out.println("negatif");
        }
      }
    }

    static void charSwitch(Character c) {
      switch (c) {
        case null -> {
          System.out.println("null");
        }
        case 'c' -> {
          System.out.println("c");
        }
        case Character c2 when Character.isUpperCase(c2) -> {
          System.out.println("upper");
        }
        default -> {
          System.out.println("lower");
        }
      }
    }

    static void byteSwitch(Byte b) {
      switch (b) {
        case null -> {
          System.out.println("null");
        }
        case 42 -> {
          System.out.println("42");
        }
        case Byte b2 when b2 > 0 -> {
          System.out.println("positif");
        }
        default -> {
          System.out.println("negatif");
        }
      }
    }

    public static void main(String[] args) {
      intSwitch(null);
      intSwitch(42);
      intSwitch(12);
      intSwitch(-1);

      charSwitch(null);
      charSwitch('c');
      charSwitch('X');
      charSwitch('x');

      byteSwitch(null);
      byteSwitch((byte) 42);
      byteSwitch((byte) 12);
      byteSwitch((byte) -1);

      shortSwitch(null);
      shortSwitch((short) 42);
      shortSwitch((short) 12);
      shortSwitch((short) -1);

      // longSwitch(null);
      // longSwitch(42L);
      // longSwitch(12L);
      // longSwitch(-1L);
      //
      // floatSwitch(null);
      // floatSwitch(42.0f);
      // floatSwitch(12.0f);
      // floatSwitch(-1.0f);
      //
      // doubleSwitch(null);
      // doubleSwitch(42.0);
      // doubleSwitch(12.0);
      // doubleSwitch(-1.0);
      //
      // booleanSwitch(null);
      // booleanSwitch(true);
      // booleanSwitch(false);
    }
  }
}
