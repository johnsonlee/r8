// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.rewrite.arrays;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class B322478366RegressionTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public CompilationMode mode;

  @Parameters(name = "{0}, mode {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), CompilationMode.values());
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .setMode(mode)
        .compile()
        .inspect(inspector -> inspect(inspector, true));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .setMode(mode)
        .enableInliningAnnotations()
        .compile()
        .inspect(inspector -> inspect(inspector, false));
  }

  private void inspect(CodeInspector inspector, boolean isD8) {
    if (isD8 || mode.isDebug()) {
      MethodSubject m1 = inspector.clazz(Main.class).uniqueMethodWithOriginalName("m1");
      assertEquals(0, m1.streamInstructions().filter(InstructionSubject::isNewArray).count());
      MethodSubject m2 = inspector.clazz(Main.class).uniqueMethodWithOriginalName("m2");
      // TODO(b/322478366): Cts test CtsPerfettoTestCases.HeapprofdJavaCtsTest#DebuggableAppOom
      //  requires that the array allocation stays.
      assertEquals(0, m2.streamInstructions().filter(InstructionSubject::isNewArray).count());
      MethodSubject m3 = inspector.clazz(Main.class).uniqueMethodWithOriginalName("m3");
      assertEquals(
          mode.isDebug() ? 1 : 0,
          m3.streamInstructions().filter(InstructionSubject::isNewArray).count());
    } else {
      assertThat(inspector.clazz(Main.class).uniqueMethodWithOriginalName("m1"), isAbsent());
      assertThat(inspector.clazz(Main.class).uniqueMethodWithOriginalName("m2"), isAbsent());
    }
  }

  public static class Main {
    @NeverInline
    public static void m1() {
      try {
        byte[] bytes = new byte[1];
        // No local information from javac for bytes, as local not observable in a debugger.
      } catch (OutOfMemoryError e) {
      }
    }

    @NeverInline
    public static void m2() {
      try {
        byte[] bytes = new byte[Integer.MAX_VALUE];
        // No local information from javac for bytes, as local not observable in a debugger.
      } catch (OutOfMemoryError e) {
      }
    }

    @NeverInline
    public static void m3() {
      try {
        byte[] bytes = new byte[1];
        // Local information from javac for bytes, as the return statement makes it observable
        // in a debugger.
        return;
      } catch (OutOfMemoryError e) {
      }
    }

    public static void main(String[] args) {
      m1();
      m2();
      m3();
    }
  }
}
