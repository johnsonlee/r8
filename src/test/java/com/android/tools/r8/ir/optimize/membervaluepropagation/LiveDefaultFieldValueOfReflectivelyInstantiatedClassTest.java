// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.membervaluepropagation;

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
public class LiveDefaultFieldValueOfReflectivelyInstantiatedClassTest extends TestBase {

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
        .addKeepClassAndDefaultConstructor(BooleanBox.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("false");
  }

  static class Main {

    public static void main(String[] args) throws Exception {
      Class<?> clazz = System.currentTimeMillis() > 0 ? BooleanBox.class : Object.class;
      BooleanBox box = (BooleanBox) clazz.getDeclaredConstructor().newInstance();
      System.out.println(box.value);
      box.set();
    }
  }

  static class BooleanBox {

    boolean value;

    BooleanBox() {}

    @NeverInline
    void set() {
      value = true;
    }
  }
}
