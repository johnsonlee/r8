// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.ifs;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.AlwaysInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NotDiamondTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public NotDiamondTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableAlwaysInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            "1", "5", "1", "5");
  }

  private void inspect(CodeInspector inspector) {
    for (FoundMethodSubject method : inspector.clazz(Main.class).allMethods()) {
      if (!method.getOriginalMethodName().equals("main")) {
        long count = method.streamInstructions().filter(InstructionSubject::isIf).count();
        assertEquals( 1, count);
      }
    }
  }

  public static class Main {

    public static void main(String[] args) {
      System.out.println(indirectEqualsSplit(2, 6));
      System.out.println(indirectEqualsSplit(3, 3));

      System.out.println(equals(2, 6));
      System.out.println(equals(3, 3));
    }

    @AlwaysInline
    public static boolean equalsSplit(int i, int j) {
      return i == j;
    }

    // Javac inverts the branches when ! is used, hence the outline with AlwaysInline to
    // reproduce the pattern if (!a).
    @AlwaysInline
    public static boolean invert(boolean b) {
      return !b;
    }

    @NeverInline
    public static int indirectEqualsSplit(int i, int j) {
      if (invert(equalsSplit(i, j))) {
        return 1;
      } else {
        return 5;
      }
    }

    @NeverInline
    public static int equals(int i, int j) {
      if (invert(i == j)) {
        return 1;
      } else {
        return 5;
      }
    }
  }
}
