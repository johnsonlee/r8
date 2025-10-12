// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.outliner.exceptions;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.ir.optimize.outliner.exceptions.ThrowBlockOutlinerArrayUseTypeTest.Main;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.Collection;
import org.junit.Test;

public class ThrowBlockOutlinerInterfaceMethodDesugaringTest extends ThrowBlockOutlinerTestBase {

  @Test
  public void testD8() throws Exception {
    runTest(testForD8(parameters));
  }

  @Test
  public void testR8() throws Exception {
    assumeRelease();
    runTest(testForR8(parameters).addKeepMainRule(Main.class));
  }

  private void runTest(
      TestCompilerBuilder<?, ?, ?, ? extends SingleTestRunResult<?>, ?> testBuilder)
      throws Exception {
    TestCompileResult<?, ?> compileResult =
        testBuilder
            .addProgramClasses(Main.class, A.class)
            .addProgramClassFileData(
                transformer(I.class)
                    .setPrivate(I.class.getDeclaredMethod("privateMethod", boolean.class))
                    .transform())
            .apply(this::configure)
            .compile()
            .inspect(inspector -> inspectOutput(inspector, testBuilder));
    compileResult
        .run(parameters.getRuntime(), Main.class, "default")
        .assertFailureWithErrorThatThrows(RuntimeException.class)
        .assertFailureWithErrorThatMatches(containsString("Default method"));
    compileResult
        .run(parameters.getRuntime(), Main.class, "private")
        .assertFailureWithErrorThatThrows(RuntimeException.class)
        .assertFailureWithErrorThatMatches(containsString("Private method"));
    compileResult
        .run(parameters.getRuntime(), Main.class, "static")
        .assertFailureWithErrorThatThrows(RuntimeException.class)
        .assertFailureWithErrorThatMatches(containsString("Static method"));
    compileResult.run(parameters.getRuntime(), Main.class, "skip").assertSuccessWithEmptyOutput();
  }

  @Override
  public void inspectOutlines(Collection<ThrowBlockOutline> outlines, DexItemFactory factory) {
    // Verify that we have a single outline with three users.
    assertEquals(1, outlines.size());
    ThrowBlockOutline outline = outlines.iterator().next();
    assertEquals(3, outline.getNumberOfUsers());
  }

  private void inspectOutput(
      CodeInspector inspector,
      TestCompilerBuilder<?, ?, ?, ? extends SingleTestRunResult<?>, ?> testBuilder) {
    if (testBuilder.isR8TestBuilder()) {
      return;
    }
    // Main, I, I$-CC, A and the synthetic outline class.
    assertEquals(
        parameters.canUseDefaultAndStaticInterfaceMethods() ? 4 : 5, inspector.allClasses().size());
  }

  static class Main {

    public static void main(String[] args) {
      I i = new A();
      i.defaultMethod(args[0].equals("default"));
      i.callPrivateMethod(args[0].equals("private"));
      I.staticMethod(args[0].equals("static"));
    }
  }

  interface I {

    default void defaultMethod(boolean fail) {
      if (fail) {
        throw new RuntimeException("Default method");
      }
    }

    // Made private by transformer.
    default void privateMethod(boolean fail) {
      if (fail) {
        throw new RuntimeException("Private method");
      }
    }

    default void callPrivateMethod(boolean fail) {
      privateMethod(fail);
    }

    static void staticMethod(boolean fail) {
      if (fail) {
        throw new RuntimeException("Static method");
      }
    }
  }

  static class A implements I {}
}
