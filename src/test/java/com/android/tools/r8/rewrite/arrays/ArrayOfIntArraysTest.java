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
public class ArrayOfIntArraysTest extends TestBase {

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
          "[[1, 2], [3, 4], [5, 6, 7], [8, 9, 10]]", "[[10, 9, 8], [7, 6, 5], [4, 3], [2, 1]]");

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
    // This test use smaller int arrays, where filled-new-array is preferred over filled-array-data.
    inspect(
        inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("m1"),
        4 + (canUseFilledNewArrayOfNonStringObjects(parameters) ? 1 : 0),
        1);
    // With catch handler the int[][] creation is not converted to filled-new-array.
    inspect(
        inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("m2"),
        4,
        compilationMode.isDebug() ? 1 : 2);
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
      int[][] array = {
        new int[] {1, 2}, new int[] {3, 4}, new int[] {5, 6, 7}, new int[] {8, 9, 10}
      };
      printArray(array);
    }

    @NeverInline
    public static void m2() {
      try {
        int[][] array = {
          new int[] {10, 9, 8}, new int[] {7, 6, 5}, new int[] {4, 3}, new int[] {2, 1}
        };
        printArray(array);
      } catch (Exception e) {
        throw new RuntimeException();
      }
    }

    @NeverInline
    public static void printArray(int[][] array) {
      System.out.print("[");
      for (int i = 0; i < array.length; i++) {
        if (i != 0) {
          System.out.print(", ");
        }
        printArray(array[i]);
      }
      System.out.println("]");
    }

    @NeverInline
    public static void printArray(int[] array) {
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
    }
  }
}
