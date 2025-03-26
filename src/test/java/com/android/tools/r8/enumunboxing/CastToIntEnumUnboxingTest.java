// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.enumunboxing;

import static com.android.tools.r8.utils.codeinspector.AssertUtils.assertFailsCompilation;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CastToIntEnumUnboxingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    assertFailsCompilation(
        () ->
            testForR8(parameters.getBackend())
                .addInnerClasses(getClass())
                .addKeepMainRule(Main.class)
                .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(MyEnum.class))
                .enableInliningAnnotations()
                .setMinApi(parameters)
                .compile()
                .run(parameters.getRuntime(), Main.class)
                .assertSuccessWithOutputLines("B"));
  }

  static class Main {

    public static void main(String[] args) {
      // Argument propagation will strengthen the parameter type of Main#test to the type of
      // MyEnum.B. We should be careful not to insert a cast to the MyEnum subclass since it could
      // be rewritten to a cast to an int.
      test(null);
      test(MyEnum.B);
    }

    @NeverInline
    static void test(MyEnum e) {
      if (e != null) {
        e.print();
      }
    }
  }

  enum MyEnum {
    A,
    B {
      @NeverInline
      @Override
      void print() {
        System.out.println("B");
      }
    };

    @NeverInline
    void print() {
      System.out.println("A");
    }
  }
}
