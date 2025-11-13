// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.outliner.bottomup.exceptions;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.R8TestCompileResultBase;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.ir.optimize.outliner.bottomup.BottomUpOutlinerTestBase;
import com.android.tools.r8.ir.optimize.outliner.bottomup.Outline;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.Collection;
import java.util.Iterator;
import org.junit.Test;

public class ThrowBlockOutlinerFeatureTest extends BottomUpOutlinerTestBase {

  @Test
  public void testR8() throws Exception {
    assumeRelease();
    R8TestCompileResultBase<?> compileResult =
        testForR8(parameters)
            .addProgramClasses(Main.class)
            .addFeatureSplit(Feature1.class)
            .addFeatureSplit(Feature2.class)
            .addKeepMainRules(Main.class, Feature1.class, Feature2.class)
            .apply(this::configure)
            .noInliningOfSynthetics()
            .compile()
            .apply(
                cr ->
                    cr.inspect(
                        inspector -> inspectOutput(inspector, cr.getSyntheticItems()),
                        inspector -> inspectFeature1Output(inspector, cr.getSyntheticItems()),
                        inspector -> inspectFeature2Output(inspector, cr.getSyntheticItems())))
            .addFeatureSplitsToRunClasspathFiles();
    compileResult
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(IllegalArgumentException.class);
    compileResult
        .run(parameters.getRuntime(), Feature1.class)
        .assertFailureWithErrorThatThrows(IllegalArgumentException.class);
    compileResult
        .run(parameters.getRuntime(), Feature2.class)
        .assertFailureWithErrorThatThrows(RuntimeException.class);
  }

  @Override
  public void inspectOutlines(Collection<Outline> outlines, DexItemFactory factory) {
    // Verify that we have two outlines after merging.
    Iterator<Outline> iterator = outlines.iterator();
    Outline outlineFromBase = iterator.next();
    Outline outlineFromFeature2;
    if (outlineFromBase.getChildren().isEmpty()) {
      outlineFromFeature2 = outlineFromBase;
      outlineFromBase = iterator.next();
    } else {
      outlineFromFeature2 = iterator.next();
    }
    assert !iterator.hasNext();

    // Verify that the outline from base has a single child.
    assertEquals(1, outlineFromBase.getChildren().size());
    assertEquals(2, outlineFromBase.getNumberOfUsers());
    assertThat(
        outlineFromBase.getMaterializedOutlineMethod().getHolder().getTypeName(),
        containsString(Main.class.getTypeName()));

    // Verify that the outline from feature 2 has no children.
    assertEquals(0, outlineFromFeature2.getChildren().size());
    assertEquals(1, outlineFromFeature2.getNumberOfUsers());
    assertThat(
        outlineFromFeature2.getMaterializedOutlineMethod().getHolder().getTypeName(),
        containsString(Feature2.class.getTypeName()));
  }

  private void inspectOutput(CodeInspector inspector, SyntheticItemsTestUtils syntheticItems) {
    assertEquals(2, inspector.allClasses().size());
    assertThat(inspector.clazz(Main.class), isPresent());

    ClassSubject outlineClassSubject =
        inspector.clazz(syntheticItems.syntheticBottomUpOutlineClass(Main.class, 0));
    assertThat(outlineClassSubject, isPresent());
    assertEquals(1, outlineClassSubject.allMethods().size());
  }

  private void inspectFeature1Output(
      CodeInspector inspector, SyntheticItemsTestUtils syntheticItems) {
    assertEquals(1, inspector.allClasses().size());
    assertThat(inspector.clazz(Feature1.class), isPresent());
  }

  private void inspectFeature2Output(
      CodeInspector inspector, SyntheticItemsTestUtils syntheticItems) {
    assertEquals(2, inspector.allClasses().size());
    assertThat(inspector.clazz(Feature2.class), isPresent());

    ClassSubject outlineClassSubject =
        inspector.clazz(syntheticItems.syntheticBottomUpOutlineClass(Feature2.class, 0));
    assertThat(outlineClassSubject, isPresent());
    assertEquals(1, outlineClassSubject.allMethods().size());
  }

  @Override
  public boolean shouldOutline(Outline outline) {
    return true;
  }

  static class Main {

    public static void main(String[] args) {
      if (args.length == 0) {
        throw new IllegalArgumentException();
      }
    }
  }

  static class Feature1 {

    public static void main(String[] args) {
      if (args.length == 0) {
        throw new IllegalArgumentException();
      }
    }
  }

  static class Feature2 {

    public static void main(String[] args) {
      if (args.length == 0) {
        throw new RuntimeException();
      }
    }
  }
}
