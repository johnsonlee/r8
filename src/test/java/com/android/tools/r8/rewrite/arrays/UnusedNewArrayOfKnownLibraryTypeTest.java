// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.rewrite.arrays;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class UnusedNewArrayOfKnownLibraryTypeTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeCfRuntime();
    testForJvm(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithEmptyOutput();
  }

  @Test
  public void testD8Release() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addInnerClasses(getClass())
        .release()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithEmptyOutput();
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithEmptyOutput();
  }

  private void inspect(CodeInspector inspector) {
    MethodSubject mainMethodSubject = inspector.clazz(Main.class).mainMethod();
    assertThat(mainMethodSubject, isPresent());
    assertTrue(mainMethodSubject.getMethod().getCode().isEmptyVoidMethod());
  }

  public static class Main {

    public static void main(String[] args) {
      // Primitives.
      boolean[] booleans = new boolean[0];
      byte[] bytes = new byte[0];
      char[] chars = new char[0];
      double[] doubles = new double[0];
      float[] floats = new float[0];
      int[] ints = new int[0];
      long[] longs = new long[0];
      short[] shorts = new short[0];

      // Boxed primitives.
      Boolean[] boxedBooleans = new Boolean[0];
      Byte[] boxedBytes = new Byte[0];
      Character[] boxedChars = new Character[0];
      Double[] boxedDoubles = new Double[0];
      Float[] boxedFloats = new Float[0];
      Integer[] boxedInts = new Integer[0];
      Long[] boxedLongs = new Long[0];
      Short[] boxedShorts = new Short[0];

      // Common classes.
      Enum<?>[] enums = new Enum<?>[0];
      Object[] objects = new Object[0];
      String[] strings = new String[0];
      StringBuilder[] stringBuilders = new StringBuilder[0];
    }
  }
}
