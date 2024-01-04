// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.arrays;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.rewrite.arrays.ConstClassArrayWithUniqueValuesTest.TestClass;
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
public class ArrayOfConstClassArraysTest extends TestBase {

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
          "[[A, B], [C, D], [E, F, G], [H, I, J]]", "[[J, I, H], [G, F, E], [D, C], [B, A]]");

  private void inspect(MethodSubject method, int dexFilledNewArray, int cfMaxLocals) {
    if (canUseFilledNewArrayOfNonStringObjects(parameters)) {
      assertEquals(
          dexFilledNewArray,
          method.streamInstructions().filter(InstructionSubject::isFilledNewArray).count());

    } else {
      assertEquals(
          0, method.streamInstructions().filter(InstructionSubject::isFilledNewArray).count());
      if (parameters.isCfRuntime()) {
        assertEquals(cfMaxLocals, method.getMethod().getCode().asCfCode().getMaxLocals());
      }
    }
  }

  private void inspect(CodeInspector inspector) {
    // TODO(b/315753861): Don't use 4 locals.
    inspect(inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("m1"), 5, 4);
    inspect(
        inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("m2"),
        0,
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
      Class<?>[][] array = {
        new Class<?>[] {A.class, B.class},
        new Class<?>[] {C.class, D.class},
        new Class<?>[] {E.class, F.class, G.class},
        new Class<?>[] {H.class, I.class, J.class}
      };
      printArray(array);
    }

    @NeverInline
    public static void m2() {
      try {
        Class<?>[][] array = {
          new Class<?>[] {J.class, I.class, H.class},
          new Class<?>[] {G.class, F.class, E.class},
          new Class<?>[] {D.class, C.class},
          new Class<?>[] {B.class, A.class}
        };
        printArray(array);
      } catch (Exception e) {
        throw new RuntimeException();
      }
    }

    @NeverInline
    public static void printArray(Class<?>[][] array) {
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
    public static void printArray(Class<?>[] array) {
      System.out.print("[");
      for (int i = 0; i < array.length; i++) {
        if (i != 0) {
          System.out.print(", ");
        }
        String simpleName = array[i].getName();
        if (simpleName.lastIndexOf("$") > 0) {
          simpleName = simpleName.substring(simpleName.lastIndexOf("$") + 1);
        }
        System.out.print(simpleName);
      }
      System.out.print("]");
    }

    public static void main(String[] args) {
      m1();
      m2();
    }
  }

  class A {}

  class B {}

  class C {}

  class D {}

  class E {}

  class F {}

  class G {}

  class H {}

  class I {}

  class J {}
}
