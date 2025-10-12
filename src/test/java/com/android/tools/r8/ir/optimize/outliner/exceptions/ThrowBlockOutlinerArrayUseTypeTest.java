// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.outliner.exceptions;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.KeepUnusedArguments;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.util.Collection;
import org.junit.Test;

public class ThrowBlockOutlinerArrayUseTypeTest extends ThrowBlockOutlinerTestBase {

  @Test
  public void testD8() throws Exception {
    runTest(testForD8(parameters));
  }

  @Test
  public void testR8() throws Exception {
    assumeRelease();
    runTest(
        testForR8(parameters)
            .addKeepMainRule(Main.class)
            .enableInliningAnnotations()
            .enableUnusedArgumentAnnotations());
  }

  private void runTest(
      TestCompilerBuilder<?, ?, ?, ? extends SingleTestRunResult<?>, ?> testBuilder)
      throws Exception {
    TestCompileResult<?, ?> compileResult =
        testBuilder.addInnerClasses(getClass()).apply(this::configure).compile();

    ClassSubject exceptionClassSubject = compileResult.inspector().clazz(MyException.class);
    compileResult
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatMatches(containsString(exceptionClassSubject.getFinalName()));
  }

  @Override
  public void inspectOutlines(Collection<ThrowBlockOutline> outlines, DexItemFactory factory) {
    assertEquals(1, outlines.size());
    ThrowBlockOutline outline = outlines.iterator().next();
    assertEquals(1, outline.getProto().getParameters().size());
    assertEquals(Main[].class.getTypeName(), outline.getProto().getParameter(0).getTypeName());
  }

  @Override
  public boolean shouldOutline(ThrowBlockOutline outline) {
    return true;
  }

  static class Main {

    public static void main(String[] args) {
      Main[] arr = new Main[0];
      if (args.length == 0) {
        throw new MyException(new StringBuilder().append(arr).toString(), arr);
      }
    }
  }

  static class MyException extends RuntimeException {

    @KeepUnusedArguments
    @NeverInline
    MyException(String msg, Main[] main) {}
  }
}
