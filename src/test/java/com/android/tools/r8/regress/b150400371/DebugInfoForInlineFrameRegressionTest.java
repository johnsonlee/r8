// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress.b150400371;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DebugInfoForInlineFrameRegressionTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public DebugInfoForInlineFrameRegressionTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testNoLinesForNonInline() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(
            transformer(InlineInto.class).removeLineNumberTable(MethodPredicate.all()).transform(),
            transformer(InlineFrom.class).removeLineNumberTable(MethodPredicate.all()).transform())
        .addKeepMainRule(InlineInto.class)
        .addKeepRules("-keepparameternames")
        .setMinApi(parameters)
        .run(parameters.getRuntime(), InlineInto.class)
        .assertSuccessWithOutputLines("42foo")
        .inspect(
            (inspector) -> {
              MethodSubject main =
                  inspector.method(InlineInto.class.getDeclaredMethod("main", String[].class));
              // Method has 2 actual lines for inlining of foo.
              // The pc2pc encoding is larger than normal encoding, so it is just the two lines.
              IntSet lines = new IntArraySet(main.getLineNumberTable().getLines());
              assertEquals(ImmutableSet.of(1, 2), lines);
            });
  }

  public static class InlineInto {
    public static void main(String[] args) {
      boolean late = System.currentTimeMillis() > 2;
      String a = late ? "42" : "43";
      String b = late ? "foo" : "bar";
      String foo = InlineFrom.foo();
      String result;
      if (System.currentTimeMillis() < 2) {
        result = a + b + foo + " that will never happen";
      } else {
        result = a + b;
      }
      System.out.println(late ? result : "never");
      if (!late) {
        System.out.println(late ? result : "never");
        System.out.println(late ? result : "never");
      }
    }
  }

  public static class InlineFrom {
    public static String foo() {
      return "bar" + System.currentTimeMillis();
    }
  }
}
