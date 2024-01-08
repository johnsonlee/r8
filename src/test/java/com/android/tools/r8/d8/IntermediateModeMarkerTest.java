// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.d8;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.OutputMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.util.function.Supplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class IntermediateModeMarkerTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("Hello, world");

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultDexRuntime().build();
  }

  private final TestParameters parameters;

  public IntermediateModeMarkerTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    Path outA =
        testForD8(Backend.DEX)
            .addProgramClasses(A.class)
            .setMinApi(AndroidApiLevel.B)
            .setOutputMode(OutputMode.DexFilePerClass)
            .setIntermediate(true)
            .compile()
            .inspect(
                inspector -> {
                  // TODO(b/147249767): Add markers to per-file intermediate builds.
                  assertTrue(inspector.getMarkers().isEmpty());
                })
            .writeToZip();
    Path outB =
        testForD8(Backend.DEX)
            .addProgramClasses(B.class)
            .setMinApi(AndroidApiLevel.B)
            .setOutputMode(OutputMode.DexFilePerClassFile)
            .setIntermediate(true)
            .compile()
            .inspect(
                inspector -> {
                  // TODO(b/147249767): Add markers to per-file intermediate builds.
                  assertTrue(inspector.getMarkers().isEmpty());
                })
            .writeToZip();

    testForD8(Backend.DEX)
        .addProgramFiles(outA, outB)
        .compile()
        .inspect(
            inspector -> {
              // TODO(b/147249767): Merging per-file intermediate builds has no marker info.
              assertTrue(inspector.getMarkers().isEmpty());
            })
        .run(parameters.getRuntime(), A.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  public static class A {

    public static void main(String[] args) {
      B.foo(() -> "Hello, ");
    }
  }

  public static class B {

    public static void foo(Supplier<String> fn) {
      bar(() -> fn.get() + "world");
    }

    public static void bar(Supplier<String> fn) {
      System.out.println(fn.get());
    }
  }
}
