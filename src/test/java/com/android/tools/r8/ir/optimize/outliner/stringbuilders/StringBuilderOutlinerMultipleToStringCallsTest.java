// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.outliner.stringbuilders;

import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.ir.optimize.outliner.exceptions.ThrowBlockOutline;
import com.android.tools.r8.ir.optimize.outliner.exceptions.ThrowBlockOutlinerTestBase;
import java.util.Collection;
import org.junit.Test;

public class StringBuilderOutlinerMultipleToStringCallsTest extends ThrowBlockOutlinerTestBase {

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
        .run(parameters.getRuntime(), Main.class, "Hel", "lo, ", "world!")
        .applyIf(
            parameters.getDexRuntimeVersion().isEqualToOneOf(Version.V5_1_1, Version.V6_0_1)
                && mode.isRelease()
                && testBuilder.isD8TestBuilder(),
            rr -> rr.assertFailureWithErrorThatThrows(NullPointerException.class),
            rr -> rr.assertFailureWithErrorThatThrows(VerifyError.class));
  }

  @Override
  public void inspectOutlines(Collection<ThrowBlockOutline> outlines, DexItemFactory factory) {}

  @Override
  public boolean shouldOutline(ThrowBlockOutline outline) {
    return true;
  }

  static class Main {

    public static void main(String[] args) {
      StringBuilder sb = new StringBuilder().append(args[0]).append(args[1]);
      System.out.print(sb.toString());
      System.out.println(sb.append(args[2]).toString());
    }
  }
}
