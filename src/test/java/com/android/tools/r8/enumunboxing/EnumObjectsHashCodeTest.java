// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.enumunboxing;

import com.android.tools.r8.KeepConstantArguments;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.util.Objects;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EnumObjectsHashCodeTest extends TestBase {

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
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(MyEnum.class))
        .enableConstantArgumentAnnotations()
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("31", "70154", "31", "null", "31", "31", "31", "961");
  }

  static class Main {

    public static void main(String[] args) {
      testFromGetter();
      testMixed();
      testNull(MyEnum.getNull());
      testNullUnboxed();
      testParam(MyEnum.getA());
      testPhi();
      testRepeated();
    }

    @NeverInline
    static void testFromGetter() {
      System.out.println(Objects.hash(MyEnum.getA()));
    }

    @NeverInline
    static void testMixed() {
      System.out.println(Objects.hash(new A(), MyEnum.A, MyEnum.B));
    }

    @NeverInline
    static void testNull(MyEnum e) {
      System.out.println(Objects.hash(e));
    }

    @NeverInline
    static void testNullUnboxed() {
      MyEnum e = null;
      MyEnum.print(e);
      System.out.println(Objects.hash(e));
    }

    @NeverInline
    static void testParam(MyEnum e) {
      System.out.println(Objects.hash(e));
    }

    @NeverInline
    static void testPhi() {
      MyEnum e = System.currentTimeMillis() > 0 ? MyEnum.A : MyEnum.B;
      System.out.println(Objects.hash(e));
    }

    @NeverInline
    static void testRepeated() {
      System.out.println(Objects.hash(MyEnum.A, MyEnum.A));
    }
  }

  static class A {

    @Override
    public int hashCode() {
      return 42;
    }
  }

  enum MyEnum {
    A,
    B;

    @NeverInline
    static MyEnum getA() {
      return System.currentTimeMillis() > 0 ? A : B;
    }

    @NeverInline
    static MyEnum getNull() {
      return System.currentTimeMillis() > 0 ? null : A;
    }

    @KeepConstantArguments
    @NeverInline
    static void print(MyEnum e) {
      System.out.println(e != null ? e.name() : "null");
    }
  }
}
