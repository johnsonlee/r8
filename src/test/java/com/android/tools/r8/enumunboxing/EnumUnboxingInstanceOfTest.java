// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.enumunboxing;


import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.EnumUnboxingInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EnumUnboxingInstanceOfTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addEnumUnboxingInspector(EnumUnboxingInspector::assertNoEnumsUnboxed)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("false", "A");
  }

  static class Main {

    public static void main(String[] args) {
      Object nullableObject = System.currentTimeMillis() > 0 ? new Object() : null;
      System.out.println(test(nullableObject));

      // Use enum instance so we don't treat MyEnum as uninstantiated.
      MyEnum e = System.currentTimeMillis() > 0 ? MyEnum.A : MyEnum.B;
      System.out.println(e.name());
    }

    @NeverInline
    static boolean test(Object e) {
      return e instanceof MyEnum;
    }
  }

  enum MyEnum {
    A,
    B
  }
}
