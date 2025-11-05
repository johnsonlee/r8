// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.outliner.bottomup.exceptions;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.isInvokeWithTarget;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.optimize.outliner.bottomup.BottomUpOutlinerTestBase;
import com.android.tools.r8.ir.optimize.outliner.bottomup.Outline;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.Lists;
import java.util.Collection;
import org.junit.Test;

public class ThrowBlockOutlinerSharedStringBuilderTest extends BottomUpOutlinerTestBase {

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
            .addInnerClasses(getClass())
            .apply(this::configure)
            .compile()
            .inspectWithSyntheticItems(this::inspectOutput);
    compileResult
        .run(parameters.getRuntime(), Main.class, "0", "1")
        .assertFailureWithErrorThatThrows(IllegalArgumentException.class)
        .assertFailureWithErrorThatMatches(containsString("i=0, j=1"));
    compileResult
        .run(parameters.getRuntime(), Main.class, "1", "0")
        .assertFailureWithErrorThatThrows(IllegalArgumentException.class)
        .assertFailureWithErrorThatMatches(containsString("j=0, i=1"));
  }

  @Override
  public void inspectOutlines(Collection<Outline> outlines, DexItemFactory factory) {
    // Verify that we have a single outline with two users.
    assertEquals(1, outlines.size());
    Outline outline = outlines.iterator().next();
    assertEquals(2, outline.getNumberOfUsers());
    assertEquals(5, outline.getProto().getArity());
    assertEquals(4, outline.getOptimizedProto(factory).getArity());

    // Verify that the last argument is known to be constant.
    AbstractValue lastArgument = ListUtils.last(outline.getArguments());
    assertTrue(lastArgument.isSingleStringValue());
    assertEquals(", k=42", lastArgument.asSingleStringValue().getDexString().toString());
  }

  private void inspectOutput(CodeInspector inspector, SyntheticItemsTestUtils syntheticItems) {
    assertEquals(2, inspector.allClasses().size());

    ClassSubject outlineClassSubject =
        inspector.clazz(syntheticItems.syntheticBottomUpOutlineClass(Main.class, 0));
    assertThat(outlineClassSubject, isPresent());
    assertEquals(1, outlineClassSubject.allMethods().size());

    // Validate that the outline uses StringBuilder and that the string ", k=42" has been moved into
    // the outline.
    MethodSubject outlineMethodSubject = outlineClassSubject.uniqueMethod();
    assertTrue(
        outlineMethodSubject
            .streamInstructions()
            .anyMatch(i -> i.isNewInstance("java.lang.StringBuilder")));
    assertTrue(outlineMethodSubject.streamInstructions().anyMatch(i -> i.isConstString(", k=42")));

    // Validate that type weakening from java.lang.String to java.lang.Object works.
    // The first parameter is java.lang.String in release mode since it is passed to
    // StringBuilder.<init>(String). The third argument has been weakened from java.lang.String
    // to java.lang.Object due to changing append(String) to append(Object).
    assertEquals(
        Lists.newArrayList(
            inspector.getTypeSubject(mode.isRelease() ? String.class : Object.class),
            inspector.getTypeSubject(int.class),
            inspector.getTypeSubject(Object.class),
            inspector.getTypeSubject(int.class)),
        outlineMethodSubject.getParameters());

    // Validate that main() no longer uses StringBuilder and that it calls the outline twice.
    MethodSubject mainMethodSubject = inspector.clazz(Main.class).mainMethod();
    assertThat(mainMethodSubject, isPresent());
    assertTrue(
        mainMethodSubject
            .streamInstructions()
            .noneMatch(i -> i.isNewInstance("java.lang.StringBuilder")));
    assertTrue(mainMethodSubject.streamInstructions().anyMatch(i -> i.isConstString("i=")));
    assertTrue(mainMethodSubject.streamInstructions().anyMatch(i -> i.isConstString("j=")));
    assertTrue(mainMethodSubject.streamInstructions().anyMatch(i -> i.isConstString(", j=")));
    assertTrue(mainMethodSubject.streamInstructions().anyMatch(i -> i.isConstString(", i=")));
    assertTrue(mainMethodSubject.streamInstructions().noneMatch(i -> i.isConstString(", k=42")));
    assertEquals(
        2,
        mainMethodSubject
            .streamInstructions()
            .filter(isInvokeWithTarget(outlineMethodSubject))
            .count());
  }

  static class Main {

    public static void main(String[] args) {
      int i = Integer.parseInt(args[0]);
      int j = Integer.parseInt(args[1]);
      if (i == 0) {
        throw new IllegalArgumentException("i=" + i + ", j=" + j + ", k=42");
      }
      if (j == 0) {
        throw new IllegalArgumentException("j=" + j + ", i=" + i + ", k=42");
      }
    }
  }
}
