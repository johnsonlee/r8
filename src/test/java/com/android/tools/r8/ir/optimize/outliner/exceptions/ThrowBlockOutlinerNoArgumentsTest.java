// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.outliner.exceptions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.BooleanBox;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ThrowBlockOutlinerNoArgumentsTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesAndAllApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    BooleanBox receivedCallback = new BooleanBox();
    testForD8(parameters)
        .addInnerClasses(getClass())
        .addOptionsModification(
            options -> {
              assertFalse(options.getThrowBlockOutlinerOptions().enable);
              options.getThrowBlockOutlinerOptions().enable = true;
              options.getThrowBlockOutlinerOptions().scannerConsumerForTesting =
                  scanner -> {
                    inspect(scanner);
                    receivedCallback.set();
                  };
            })
        .release()
        .setMinApi(parameters)
        .compile();
    assertTrue(receivedCallback.isTrue());
  }

  private void inspect(ThrowBlockOutlinerScanner scanner) {
    // Verify that we have a single outline with three uses.
    Collection<ThrowBlockOutline> outlines = scanner.getOutlines();
    assertEquals(1, outlines.size());

    ThrowBlockOutline outline = outlines.iterator().next();
    assertEquals(3, outline.getNumberOfUsers());
  }

  static class Main {

    public static void main(String[] args) {
      if (args.length == 0) {
        throw new IllegalArgumentException();
      }
      if (args.length == 1) {
        throw new IllegalArgumentException();
      }
      if (args.length == 2) {
        throw new IllegalArgumentException();
      }
    }
  }
}
