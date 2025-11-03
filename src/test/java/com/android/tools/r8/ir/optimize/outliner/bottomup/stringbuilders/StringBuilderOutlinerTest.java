// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.outliner.bottomup.stringbuilders;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.ir.optimize.outliner.bottomup.BottomUpOutlinerTestBase;
import com.android.tools.r8.ir.optimize.outliner.bottomup.Outline;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.Collection;
import org.junit.Test;

public class StringBuilderOutlinerTest extends BottomUpOutlinerTestBase {

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
    testBuilder
        .addInnerClasses(getClass())
        .apply(this::configure)
        .compile()
        .inspect(this::inspectOutput)
        .run(parameters.getRuntime(), Main.class, "Hel", "lo", ", world", "!")
        .assertSuccessWithOutputLines("Hello, world!");
  }

  @Override
  public void inspectOutlines(Collection<Outline> outlines, DexItemFactory factory) {
    assertEquals(1, outlines.size());
    Outline outline = outlines.iterator().next();
    assertEquals(2, outline.getNumberOfUsers());
  }

  private void inspectOutput(CodeInspector inspector) {
    MethodSubject mainMethod = inspector.clazz(Main.class).mainMethod();
    assertTrue(mainMethod.streamInstructions().noneMatch(InstructionSubject::isNewInstance));
  }

  @Override
  public boolean shouldOutline(Outline outline) {
    return true;
  }

  static class Main {

    public static void main(String[] args) {
      String s1 = new StringBuilder().append(args[0]).append(args[1]).toString();
      String s2 = new StringBuilder().append(args[2]).append(args[3]).toString();
      System.out.print(s1);
      System.out.println(s2);
    }
  }
}
