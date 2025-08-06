// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.outliner.exceptions;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.isInvokeWithTarget;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.BooleanBox;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ThrowBlockOutlinerNoArgumentsTest extends TestBase {

  @Parameter(0)
  public boolean minimizeSyntheticNames;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, minimizeSyntheticNames: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withDexRuntimesAndAllApiLevels().build());
  }

  @Test
  public void test() throws Exception {
    BooleanBox receivedCallback = new BooleanBox();
    TestCompileResult<?, ?> compileResult =
        testForD8(parameters)
            .addInnerClasses(getClass())
            .addOptionsModification(
                options -> {
                  options.desugarSpecificOptions().minimizeSyntheticNames = minimizeSyntheticNames;
                  assertFalse(options.getThrowBlockOutlinerOptions().enable);
                  options.getThrowBlockOutlinerOptions().enable = true;
                  options.getThrowBlockOutlinerOptions().outlineConsumerForTesting =
                      outlines -> {
                        inspectOutlines(outlines);
                        receivedCallback.set();
                      };
                })
            .release()
            .setMinApi(parameters)
            .compile()
            .inspect(this::inspectOutput);
    assertTrue(receivedCallback.isTrue());

    for (int i = 0; i < 3; i++) {
      compileResult
          .run(parameters.getRuntime(), Main.class, Integer.toString(i))
          .assertFailureWithErrorThatThrows(IllegalArgumentException.class);
    }
    compileResult
        .run(parameters.getRuntime(), Main.class, Integer.toString(3))
        .assertFailureWithErrorThatThrows(RuntimeException.class);
    compileResult
        .run(parameters.getRuntime(), Main.class, Integer.toString(42))
        .assertSuccessWithEmptyOutput();
  }

  private void inspectOutlines(Collection<ThrowBlockOutline> outlines) {
    // Verify that we have two outlines with one and three users, respectively.
    assertEquals(2, outlines.size());
    IntSet numberOfUsers = new IntArraySet();
    for (ThrowBlockOutline outline : outlines) {
      numberOfUsers.add(outline.getNumberOfUsers());
    }
    assertTrue(numberOfUsers.contains(1));
    assertTrue(numberOfUsers.contains(3));
  }

  private void inspectOutput(CodeInspector inspector) {
    assertEquals(2, inspector.allClasses().size());

    MethodSubject mainMethodSubject = inspector.clazz(Main.class).mainMethod();
    assertThat(mainMethodSubject, isPresent());

    ClassSubject outlineClassSubject =
        inspector.clazz(
            minimizeSyntheticNames
                ? SyntheticItemsTestUtils.syntheticClassWithMinimalName(Main.class, 0)
                : SyntheticItemsTestUtils.syntheticThrowBlockOutlineClass(Main.class, 0));
    assertThat(outlineClassSubject, isPresent());
    assertEquals(1, outlineClassSubject.allMethods().size());

    MethodSubject outlineMethodSubject = outlineClassSubject.uniqueMethod();
    assertEquals(
        3,
        mainMethodSubject
            .streamInstructions()
            .filter(isInvokeWithTarget(outlineMethodSubject))
            .count());
  }

  static class Main {

    public static void main(String[] args) {
      int i = Integer.parseInt(args[0]);
      if (i == 0) {
        throw new IllegalArgumentException();
      }
      if (i == 1) {
        throw new IllegalArgumentException();
      }
      if (i == 2) {
        throw new IllegalArgumentException();
      }
      if (i == 3) {
        throw new RuntimeException();
      }
    }
  }
}
