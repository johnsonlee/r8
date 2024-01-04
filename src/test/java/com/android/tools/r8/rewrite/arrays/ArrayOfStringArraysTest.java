// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.arrays;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.rewrite.arrays.ArrayOfConstClassArraysTest.TestClass;
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
public class ArrayOfStringArraysTest extends TestBase {

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
          "[[A, B], [C, D], [E, F, G], [H, I, J]]",
          "[[J, I, H], [G, F, E], [D, C], [B, A]]",
          "[[[A, B, C]], [[D, E, F]], [[G, H]], [[I, J]]]",
          "[[[J, I, H]], [[G, F, E]], [[D, C]], [[B, A]]]",
          "[[J, I, H], [G, F, E], [D, C], [B, A]]");

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
        (canUseFilledNewArrayOfStringObjects(parameters) ? 4 : 0)
            + (canUseFilledNewArrayOfNonStringObjects(parameters) ? 1 : 0),
        // TODO(b/315753861): Don't use 4 locals.
        4);
    inspect(
        inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("m2"),
        0,
        compilationMode.isDebug() ? 2 : 1);
    inspect(
        inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("m3"),
        (canUseFilledNewArrayOfStringObjects(parameters) ? 4 : 0)
            + (canUseFilledNewArrayOfNonStringObjects(parameters) ? 5 : 0),
        5);
    inspect(
        inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("m4"),
        0,
        compilationMode.isDebug() ? 2 : 1);
    inspect(
        inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("m5"),
        (canUseFilledNewArrayOfStringObjects(parameters) ? 4 : 0)
            + (canUseFilledNewArrayOfNonStringObjects(parameters) ? 1 : 0),
        compilationMode.isDebug() ? 6 : 4);
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
      String[][] array = {
        new String[] {"A", "B"},
        new String[] {"C", "D"},
        new String[] {"E", "F", "G"},
        new String[] {"H", "I", "J"}
      };
      printArray(array);
      System.out.println();
    }

    @NeverInline
    public static void m2() {
      try {
        String[][] array = {
          new String[] {"J", "I", "H"},
          new String[] {"G", "F", "E"},
          new String[] {"D", "C"},
          new String[] {"B", "A"}
        };
        printArray(array);
        System.out.println();
      } catch (Exception e) {
        throw new RuntimeException();
      }
    }

    @NeverInline
    public static void m3() {
      String[][][] array = {
        new String[][] {new String[] {"A", "B", "C"}},
        new String[][] {new String[] {"D", "E", "F"}},
        new String[][] {new String[] {"G", "H"}},
        new String[][] {new String[] {"I", "J"}}
      };
      printArray(array);
      System.out.println();
    }

    @NeverInline
    public static void m4() {
      try {
        String[][][] array = {
          new String[][] {new String[] {"J", "I", "H"}},
          new String[][] {new String[] {"G", "F", "E"}},
          new String[][] {new String[] {"D", "C"}},
          new String[][] {new String[] {"B", "A"}}
        };
        printArray(array);
        System.out.println();
      } catch (Exception e) {
        throw new RuntimeException();
      }
    }

    @NeverInline
    public static void m5() {
      String[] a1 = new String[] {"J", "I", "H"};
      String[] a2 = new String[] {"G", "F", "E"};
      String[] a3 = new String[] {"D", "C"};
      String[] a4 = new String[] {"B", "A"};
      try {
        String[][] array = {a1, a2, a3, a4};
        printArray(array);
        System.out.println();
      } catch (Exception e) {
        throw new RuntimeException();
      }
    }

    @NeverInline
    public static void printArray(String[][][] array) {
      System.out.print("[");
      for (int i = 0; i < array.length; i++) {
        if (i != 0) {
          System.out.print(", ");
        }
        printArray(array[i]);
      }
      System.out.print("]");
    }

    @NeverInline
    public static void printArray(String[][] array) {
      System.out.print("[");
      for (int i = 0; i < array.length; i++) {
        if (i != 0) {
          System.out.print(", ");
        }
        printArray(array[i]);
      }
      System.out.print("]");
    }

    @NeverInline
    public static void printArray(String[] array) {
      System.out.print("[");
      for (int i = 0; i < array.length; i++) {
        if (i != 0) {
          System.out.print(", ");
        }
        System.out.print(array[i]);
      }
      System.out.print("]");
    }

    public static void main(String[] args) {
      m1();
      m2();
      m3();
      m4();
      m5();
    }
  }
}
