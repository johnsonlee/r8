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
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.Collection;
import org.junit.Test;

public class StringBuilderOutlinerBooleanTypeTest extends BottomUpOutlinerTestBase {

  @Test
  public void testD8() throws Exception {
    runTest(testForD8(parameters));
  }

  @Test
  public void testR8() throws Exception {
    assumeRelease();
    runTest(testForR8(parameters).addKeepAllClassesRule());
  }

  private void runTest(
      TestCompilerBuilder<?, ?, ?, ? extends SingleTestRunResult<?>, ?> testBuilder)
      throws Exception {
    testBuilder
        .addInnerClasses(getClass())
        .apply(this::configure)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("true");
  }

  @Override
  public void inspectOutlines(Collection<Outline> outlines, DexItemFactory factory) {
    if (parameters.getApiLevel().isLessThan(AndroidApiLevel.L)) {
      assertTrue(outlines.isEmpty());
    } else {
      assertEquals(1, outlines.size());
      Outline outline = outlines.iterator().next();
      assertTrue(outline.getProto().getParameter(0).isIntType());
    }
  }

  @Override
  public boolean shouldOutline(Outline outline) {
    return true;
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println(toString(true));
    }

    static String toString(boolean b) {
      return new StringBuilder().append(b).toString();
    }
  }
}
