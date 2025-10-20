// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debuginfo;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EnsureNoDebugInfoEmittedForPcOnlyFirstOutlineTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void testDebugInfo() throws Exception {
    testForR8(Backend.DEX)
        .addInnerClasses(getClass())
        .addKeepAllClassesRule()
        .setMinApi(AndroidApiLevel.B)
        .compile()
        .apply(x -> System.out.println(x.getProguardMap()))
        .inspect(inspector -> inspect(inspector, false, false));
  }

  @Test
  public void testDebugInfoWithPcBasedEncoding() throws Exception {
    testForR8(Backend.DEX)
        .addInnerClasses(getClass())
        .addKeepAllClassesRule()
        .addOptionsModification(options -> options.testing.forcePcBasedEncoding = true)
        .setMinApi(AndroidApiLevel.B)
        .compile()
        .apply(x -> System.out.println(x.getProguardMap()))
        .inspect(inspector -> inspect(inspector, false, true));
  }

  @Test
  public void testNativePc() throws Exception {
    testForR8(Backend.DEX)
        .addInnerClasses(getClass())
        .addKeepAllClassesRule()
        .setMinApi(AndroidApiLevel.ANDROID_PLATFORM_CONSTANT)
        .compile()
        .apply(x -> System.out.println(x.getProguardMap()))
        .inspect(inspector -> inspect(inspector, true, false));
  }

  @Test
  public void testNativePcWithPcBasedEncoding() throws Exception {
    testForR8(Backend.DEX)
        .addInnerClasses(getClass())
        .addKeepAllClassesRule()
        .addOptionsModification(options -> options.testing.forcePcBasedEncoding = true)
        .setMinApi(AndroidApiLevel.ANDROID_PLATFORM_CONSTANT)
        .compile()
        .apply(x -> System.out.println(x.getProguardMap()))
        .inspect(inspector -> inspect(inspector, true, true));
  }

  private void inspect(CodeInspector inspector, boolean nativePc, boolean pcBasedEncoding)
      throws NoSuchMethodException {
    ClassSubject clazz = inspector.clazz(Main.class);
    DexCode first =
        clazz.method(Main.class.getDeclaredMethod("foo")).getMethod().getCode().asDexCode();
    if (nativePc) {
      assertNull(first.getDebugInfo());
    } else {
      assertNotNull(first.getDebugInfo());
    }

    DexCode second =
        clazz
            .method(Main.class.getDeclaredMethod("foo", Object.class))
            .getMethod()
            .getCode()
            .asDexCode();
    assertNotNull(second.getDebugInfo());

    DexCode third =
        clazz
            .method(Main.class.getDeclaredMethod("foo", String.class))
            .getMethod()
            .getCode()
            .asDexCode();
    assertNotNull(third.getDebugInfo());

    if (nativePc) {
      assertEquals(first.codeSizeInBytes(), second.getDebugInfo().getStartLine());
      assertEquals(first.codeSizeInBytes() + 1, third.getDebugInfo().getStartLine());
    } else {
      assertEquals(1, first.getDebugInfo().getStartLine());
      if (pcBasedEncoding) {
        assertEquals(9, second.getDebugInfo().getStartLine());
        assertEquals(10, third.getDebugInfo().getStartLine());
      } else {
        assertEquals(2, second.getDebugInfo().getStartLine());
        assertEquals(3, third.getDebugInfo().getStartLine());
      }
    }
  }

  static class Main {

    static void foo() {
      System.out.println("foo");
    }

    static void foo(Object o) {
      System.out.println("foo");
    }

    static void foo(String s) {
      System.out.println("foo");
    }
  }
}
