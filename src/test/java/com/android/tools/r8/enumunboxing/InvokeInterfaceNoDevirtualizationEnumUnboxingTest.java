// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.enumunboxing;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.AssertUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvokeInterfaceNoDevirtualizationEnumUnboxingTest extends EnumUnboxingTestBase {

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public InvokeInterfaceNoDevirtualizationEnumUnboxingTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void test() throws Exception {
    AssertUtils.assertFailsCompilation(
        () ->
            testForR8(parameters)
                .addInnerClasses(getClass())
                .addKeepMainRule(Main.class)
                .addKeepRules(enumKeepRules.getKeepRules())
                // Disable devirtualization so that the enum unboxing rewriter sees the call to
                // I#greet instead of MyEnum#greet.
                .addOptionsModification(options -> options.enableDevirtualization = false)
                .addOptionsModification(
                    options -> enableEnumOptions(options, enumValueOptimization))
                .enableInliningAnnotations()
                .enableNoVerticalClassMergingAnnotations()
                .compile()
                .run(parameters.getRuntime(), Main.class)
                .assertSuccessWithOutputLines("Hello, world!"));
  }

  static class Main {

    public static void main(String[] args) {
      MyEnum e = System.currentTimeMillis() > 0 ? MyEnum.A : MyEnum.B;
      greet(e);
    }

    static void greet(I i) {
      i.greet();
    }
  }

  @NoVerticalClassMerging
  interface I {

    void greet();
  }

  enum MyEnum implements I {
    A,
    B;

    @NeverInline
    @Override
    public void greet() {
      System.out.println("Hello, world!");
    }
  }
}
