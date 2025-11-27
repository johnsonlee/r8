// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.repackage;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Reproduction of b/328837166. */
@RunWith(Parameterized.class)
public class MappingFileAfterRepackagingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean repackage;

  @Parameters(name = "{0}, repackage: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepAttributeLineNumberTable()
        .addKeepAttributeSourceFile()
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector.assertIsCompleteMergeGroup(A.class, B.class).assertNoOtherClassesMerged())
        .applyIf(!repackage, TestShrinkerBuilder::addDontRepackage)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A")
        .apply(
            runResult -> {
              long syntheticMatches =
                  StringUtils.splitLines(runResult.proguardMap()).stream()
                      .filter(
                          line ->
                              line.contains(
                                  "java.lang.String MappingFileAfterRepackagingTest$A.toString()"))
                      .count();
              assertEquals(
                  repackage ? 1 + BooleanUtils.intValue(parameters.isDexRuntime()) : 0,
                  syntheticMatches);

              long unqualifiedMatches =
                  StringUtils.splitLines(runResult.proguardMap()).stream()
                      .filter(line -> line.contains("java.lang.String toString()"))
                      .count();
              assertEquals(
                  (repackage ? 1 : 2 + BooleanUtils.intValue(parameters.isDexRuntime())),
                  unqualifiedMatches);
            });
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println(System.currentTimeMillis() > 0 ? new A() : new B());
    }
  }

  public static class A {

    @NeverInline
    public String toString() {
      System.out.print("");
      return "A";
    }
  }

  static class B {

    public String toString() {
      return "B";
    }
  }
}
