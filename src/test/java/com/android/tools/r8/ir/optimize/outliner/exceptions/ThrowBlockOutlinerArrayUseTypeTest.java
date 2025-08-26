// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.outliner.exceptions;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.graph.DexItemFactory;
import java.util.Collection;
import org.junit.Test;

public class ThrowBlockOutlinerArrayUseTypeTest extends ThrowBlockOutlinerTestBase {

  @Test
  public void test() throws Exception {
    testForD8(parameters)
        .addInnerClasses(getClass())
        .apply(this::configure)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(MyException.class);
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

    MyException(String msg, Main[] main) {}
  }
}
