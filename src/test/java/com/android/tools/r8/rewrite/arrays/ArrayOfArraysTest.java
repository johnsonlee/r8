// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.arrays;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ArrayOfArraysTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public CompilationMode compilationMode;

  @Parameters(name = "{0}, mode = {1}")
  public static Iterable<?> data() {
    return buildParameters(
        getTestParameters()
            .withDefaultCfRuntime()
            .withDexRuntimesAndAllApiLevels()
            .withAllApiLevelsAlsoForCf()
            .build(),
        CompilationMode.values());
  }

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines(
          "[class [B, class [S, class [I, class [J, class [C, class [F, class [D, class"
              + " [Ljava.lang.String;, [class [B, class [S, class [I, class [J, class [C, class [F,"
              + " class [D, class [Ljava.lang.String;]]",
          "[class [B, class [S, class [I, class [J, class [C, class [F, class [D, class"
              + " [Ljava.lang.String;, [class [B, class [S, class [I, class [J, class [C, class [F,"
              + " class [D, class [Ljava.lang.String;]]");

  private void inspect(MethodSubject method, int dexFilledNewArray, int cfMaxLocals) {
    if (parameters.isDexRuntime()) {
      assertEquals(
          dexFilledNewArray,
          method.streamInstructions().filter(InstructionSubject::isFilledNewArray).count());

    } else {
      assertEquals(
          0, method.streamInstructions().filter(InstructionSubject::isFilledNewArray).count());
      assertEquals(cfMaxLocals, method.getMethod().getCode().asCfCode().getMaxLocals());
    }
  }

  private void inspect(CodeInspector inspector) {
    inspect(
        inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("m1"),
        2
            + (canUseFilledNewArrayOfStringObjects(parameters) ? 2 : 0)
            + (canUseFilledNewArrayOfNonStringObjects(parameters) ? 2 : 0),
        2);
    inspect(inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("m2"), 2, 2);
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .setMode(compilationMode)
        .run(parameters.getRuntime(), TestClass.class)
        .inspect(this::inspect)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .setMode(compilationMode)
        .enableInliningAnnotations()
        .addDontObfuscate()
        .run(parameters.getRuntime(), TestClass.class)
        .inspect(this::inspect)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  public static final class TestClass {

    @NeverInline
    public static void m1() {
      Object[] array = {
        new byte[] {(byte) 1},
        new short[] {(short) 1},
        new int[] {1},
        new long[] {1L},
        new char[] {(char) 1},
        new float[] {1.0f},
        new double[] {1.0d},
        new String[] {"one"},
        new Object[] {
          new byte[] {(byte) 2},
          new short[] {(short) 2},
          new int[] {2},
          new long[] {2L},
          new char[] {(char) 2},
          new float[] {2.0f},
          new double[] {2.0d},
          new String[] {"two"},
        }
      };
      printArray(array);
      System.out.println();
    }

    @NeverInline
    public static void m2() {
      try {
        Object[] array = {
          new byte[] {(byte) 1},
          new short[] {(short) 1},
          new int[] {1},
          new long[] {1L},
          new char[] {(char) 1},
          new float[] {1.0f},
          new double[] {1.0d},
          new String[] {"one"},
          new Object[] {
            new byte[] {(byte) 2},
            new short[] {(short) 2},
            new int[] {2},
            new long[] {2L},
            new char[] {(char) 2},
            new float[] {2.0f},
            new double[] {2.0d},
            new String[] {"two"},
          }
        };
        printArray(array);
        System.out.println();
      } catch (Exception e) {
        throw new RuntimeException();
      }
    }

    @NeverInline
    public static void printArray(Object[] array) {
      System.out.print("[");
      for (int i = 0; i < array.length; i++) {
        if (i != 0) {
          System.out.print(", ");
        }
        if (array[i].getClass().isArray()) {
          if (array[i] instanceof Object[] && !(array[i] instanceof String[])) {
            printArray((Object[]) array[i]);
          } else {
            System.out.print(array[i].getClass());
          }
        } else {
          System.out.print("Unexpected " + array[i].getClass());
        }
      }
      System.out.print("]");
    }

    public static void main(String[] args) {
      m1();
      m2();
    }
  }
}
