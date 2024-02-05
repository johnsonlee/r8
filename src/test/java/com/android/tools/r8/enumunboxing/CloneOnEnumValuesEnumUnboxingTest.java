// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.util.ArrayList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CloneOnEnumValuesEnumUnboxingTest extends TestBase {

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
        .addEnumUnboxingInspector(
            inspector -> {
              inspector.assertUnboxed(MyEnum1.class);
              inspector.assertNotUnboxed(MyEnum2.class);
              inspector.assertNotUnboxed(MyEnum3.class);
            })
        .setMinApi(parameters)
        .enableInliningAnnotations()
        .addDontObfuscate()
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A", "2", "B", "2", "A", "A", "B", "B");
  }

  static class Main {
    @NeverInline
    public static void m1(MyEnum1[] values) {
      for (MyEnum1 e : values.clone()) {
        System.out.println(e.name());
        System.out.println(values.clone().length);
      }
    }

    @NeverInline
    public static void m2(MyEnum2[] values) {
      for (MyEnum2 e : values) {
        System.out.println(e.name());
        System.out.println(values.clone()[e.ordinal()]);
      }
    }

    @NeverInline
    public static void m3(MyEnum3[] values) {
      new ArrayList<>().add(values);
    }

    public static void main(String[] args) {
      m1(MyEnum1.values());
      m2(MyEnum2.values());
      m3(MyEnum3.values());
    }
  }

  enum MyEnum1 {
    A,
    B
  }

  enum MyEnum2 {
    A,
    B
  }

  enum MyEnum3 {
    A,
    B
  }
}
