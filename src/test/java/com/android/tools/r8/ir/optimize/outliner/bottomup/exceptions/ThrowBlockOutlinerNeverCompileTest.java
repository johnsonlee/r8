// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.outliner.bottomup.exceptions;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.ir.optimize.outliner.bottomup.BottomUpOutlinerTestBase;
import com.android.tools.r8.ir.optimize.outliner.bottomup.Outline;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.Collection;
import org.junit.Test;

public class ThrowBlockOutlinerNeverCompileTest extends BottomUpOutlinerTestBase {

  @Test
  public void testD8() throws Exception {
    runTest(testForD8(parameters), testForD8(parameters));
  }

  @Test
  public void testR8() throws Exception {
    assumeRelease();
    runTest(
        testForR8(parameters).addKeepMainRule(Main.class).noInliningOfSynthetics(),
        testForR8(parameters).addKeepMainRule(Main.class).noInliningOfSynthetics());
  }

  private void runTest(
      TestCompilerBuilder<?, ?, ?, ? extends SingleTestRunResult<?>, ?> testBuilder,
      TestCompilerBuilder<?, ?, ?, ? extends SingleTestRunResult<?>, ?> otherTestBuilder)
      throws Exception {
    long oatSize =
        testBuilder
            .addInnerClasses(getClass())
            .addOptionsModification(
                options -> {
                  assertFalse(options.getBottomUpOutlinerOptions().neverCompile);
                })
            .apply(this::configure)
            .compile()
            .inspectWithSyntheticItems(
                (inspector, syntheticItems) -> inspectOutput(inspector, syntheticItems, false))
            .runDex2Oat(parameters.getRuntime())
            .getOatSizeOrDefault(-1);
    assertTrue(0 < oatSize);

    long oatSizeNeverCompile =
        otherTestBuilder
            .addInnerClasses(getClass())
            .addOptionsModification(
                options -> {
                  assertFalse(options.getBottomUpOutlinerOptions().neverCompile);
                  options.getBottomUpOutlinerOptions().neverCompile = true;
                })
            .apply(this::configure)
            .compile()
            .inspectWithSyntheticItems(
                (inspector, syntheticItems) -> inspectOutput(inspector, syntheticItems, true))
            .runDex2Oat(parameters.getRuntime())
            .getOatSizeOrDefault(-1);
    assertTrue(0 < oatSizeNeverCompile);
    // TODO(b/434769547): Why is the @NeverCompile version not smaller?
    assertEquals(oatSize, oatSizeNeverCompile);
  }

  @Override
  public void inspectOutlines(Collection<Outline> outlines, DexItemFactory factory) {
    // Intentionally empty.
  }

  private void inspectOutput(
      CodeInspector inspector, SyntheticItemsTestUtils syntheticItems, boolean neverCompile) {
    assertEquals(2, inspector.allClasses().size());

    ClassSubject outlineClassSubject =
        inspector.clazz(syntheticItems.syntheticBottomUpOutlineClass(Main.class, 0));
    assertThat(outlineClassSubject, isPresent());
    assertEquals(1, outlineClassSubject.allMethods().size());

    MethodSubject outlineMethodSubject = outlineClassSubject.uniqueMethod();
    assertEquals(neverCompile ? 1 : 0, outlineMethodSubject.annotations().size());
  }

  @Override
  public boolean shouldOutline(Outline outline) {
    return true;
  }

  static class Main {

    public static void main(String[] args) {
      int i = Integer.parseInt(args[0]);
      if (i == 0) {
        throw new IllegalArgumentException();
      }
    }
  }
}
