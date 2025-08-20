// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.outliner.exceptions;

import static com.google.common.base.Predicates.alwaysTrue;
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
public class ThrowBlockOutlinerUseTypeTest extends TestBase {

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
              options.getThrowBlockOutlinerOptions().outlineConsumerForTesting =
                  outlines -> {
                    inspectOutlines(outlines);
                    receivedCallback.set();
                  };
              options.getThrowBlockOutlinerOptions().outlineStrategyForTesting = alwaysTrue();
            })
        .release()
        .compile()
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/440044482): Should fail with MyException.
        .assertFailureWithErrorThatThrows(VerifyError.class);
    assertTrue(receivedCallback.isTrue());
  }

  private void inspectOutlines(Collection<ThrowBlockOutline> outlines) {
    assertEquals(1, outlines.size());
    ThrowBlockOutline outline = outlines.iterator().next();
    assertEquals(1, outline.getProto().getParameters().size());
    // TODO(b/440044482): The parameter type should be Main.
    assertEquals("java.lang.Object", outline.getProto().getParameter(0).getTypeName());
  }

  static class Main {

    public static void main(String[] args) {
      Main main = new Main();
      if (args.length == 0) {
        throw new MyException(new StringBuilder().append(main).toString(), main);
      }
    }
  }

  static class MyException extends RuntimeException {

    MyException(String msg, Main main) {}
  }
}
