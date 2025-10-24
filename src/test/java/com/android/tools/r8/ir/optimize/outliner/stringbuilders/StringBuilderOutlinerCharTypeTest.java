// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.outliner.stringbuilders;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.ir.optimize.outliner.exceptions.ThrowBlockOutline;
import com.android.tools.r8.ir.optimize.outliner.exceptions.ThrowBlockOutlinerTestBase;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.Collection;
import org.junit.Test;

public class StringBuilderOutlinerCharTypeTest extends ThrowBlockOutlinerTestBase {

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
        .assertSuccessWithOutputLines("a");
  }

  @Override
  public void inspectOutlines(Collection<ThrowBlockOutline> outlines, DexItemFactory factory) {
    if (parameters.getApiLevel().isLessThan(AndroidApiLevel.L)) {
      assertTrue(outlines.isEmpty());
    } else {
      assertEquals(1, outlines.size());
      ThrowBlockOutline outline = outlines.iterator().next();
      assertTrue(outline.getProto().getParameter(0).isIntType());
    }
  }

  @Override
  public boolean shouldOutline(ThrowBlockOutline outline) {
    return true;
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println(toString('a'));
    }

    static String toString(char c) {
      return new StringBuilder().append(c).toString();
    }
  }
}
