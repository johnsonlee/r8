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
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.util.Collection;
import org.junit.Test;

public class ThrowBlockOutlinerSyntheticSharingTest extends BottomUpOutlinerTestBase {

  @Test
  public void test() throws Exception {
    TestCompileResult<?, ?> compileResult =
        testForD8(parameters.getBackend())
            .addProgramClasses(Main.class)
            .apply(this::configure)
            .release()
            .setIntermediate(true)
            .setMinApi(parameters)
            .compile();
    TestCompileResult<?, ?> otherCompileResult =
        testForD8(parameters.getBackend())
            .addProgramClasses(OtherMain.class)
            .apply(this::configure)
            .release()
            .setIntermediate(true)
            .setMinApi(parameters)
            .compile();
    testForD8(parameters.getBackend())
        .addProgramFiles(compileResult.writeToZip(), otherCompileResult.writeToZip())
        .collectSyntheticItems()
        .release()
        .setMinApi(parameters)
        .compile()
        .inspectWithSyntheticItems(
            (inspector, syntheticItems) -> {
              // The output should contain Main, OtherMain, and a *single* synthetic class.
              assertEquals(3, inspector.allClasses().size());
              ClassSubject syntheticClassSubject =
                  inspector.clazz(syntheticItems.syntheticBottomUpOutlineClass(Main.class, 0));
              assertThat(syntheticClassSubject, isPresent());
              assertEquals(1, syntheticClassSubject.allMethods().size());

              ClassSubject mainClassSubject = inspector.clazz(Main.class);
              assertThat(mainClassSubject, isPresent());
              assertThat(
                  mainClassSubject.mainMethod(),
                  invokesMethod(syntheticClassSubject.uniqueMethod()));

              ClassSubject otherMainClassSubject = inspector.clazz(OtherMain.class);
              assertThat(otherMainClassSubject, isPresent());
              assertThat(
                  otherMainClassSubject.mainMethod(),
                  invokesMethod(syntheticClassSubject.uniqueMethod()));
            });
  }

  @Override
  public void inspectOutlines(Collection<Outline> outlines, DexItemFactory factory) {
    // Verify that a single outline was created in each intermediate build.
    assertEquals(1, outlines.size());
  }

  @Override
  public boolean shouldOutline(Outline outline) {
    return true;
  }

  static class Main {

    public static void main(String[] args) {
      if (args == null) {
        throw new IllegalArgumentException();
      }
    }
  }

  static class OtherMain {

    public static void main(String[] args) {
      if (args == null) {
        throw new IllegalArgumentException();
      }
    }
  }
}
