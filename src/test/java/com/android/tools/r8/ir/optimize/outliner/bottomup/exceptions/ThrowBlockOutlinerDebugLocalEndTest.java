// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.outliner.bottomup.exceptions;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.ir.optimize.outliner.bottomup.BottomUpOutlinerTestBase;
import com.android.tools.r8.ir.optimize.outliner.bottomup.Outline;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.Collection;
import org.junit.Test;

public class ThrowBlockOutlinerDebugLocalEndTest extends BottomUpOutlinerTestBase {

  @Test
  public void test() throws Exception {
    TestCompileResult<?, ?> compileResult =
        testForD8(parameters)
            .addInnerClasses(getClass())
            .apply(this::configure)
            .compile()
            .inspectWithSyntheticItems(this::inspectOutput);
    compileResult
        .run(parameters.getRuntime(), Main.class, "0")
        .assertFailureWithErrorThatThrows(IllegalArgumentException.class);
    compileResult.run(parameters.getRuntime(), Main.class, "1").assertSuccessWithEmptyOutput();
  }

  @Override
  public void inspectOutlines(Collection<Outline> outlines, DexItemFactory factory) {
    // Verify that we have a single outline.
    assertEquals(1, outlines.size());
  }

  private void inspectOutput(CodeInspector inspector, SyntheticItemsTestUtils syntheticItems) {
    assertEquals(2, inspector.allClasses().size());

    MethodSubject mainMethodSubject = inspector.clazz(Main.class).mainMethod();
    assertThat(mainMethodSubject, isPresent());

    ClassSubject outlineClassSubject =
        inspector.clazz(syntheticItems.syntheticBottomUpOutlineClass(Main.class, 0));
    assertThat(outlineClassSubject, isPresent());
    assertEquals(1, outlineClassSubject.allMethods().size());

    MethodSubject outlineMethodSubject = outlineClassSubject.uniqueMethod();
    assertThat(mainMethodSubject, invokesMethod(outlineMethodSubject));
  }

  @Override
  public boolean shouldOutline(Outline outline) {
    return true;
  }

  static class Main {

    public static void main(String[] args) {
      int i = Integer.parseInt(args[0]);
      if (i == 0) {
        StringBuilder sb = new StringBuilder();
        {
          String s = "Hello, world!";
          sb.append(s);
        }
        throw new IllegalArgumentException(sb.toString());
      }
    }
  }
}
