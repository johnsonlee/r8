// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ParameterWithUnknownDynamicTypeIntoFieldTest extends TestBase {

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
        .enableInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject aClassSubject = inspector.clazz(A.class);
              assertThat(aClassSubject, isPresent());

              ClassSubject bClassSubject = inspector.clazz(B.class);
              assertThat(bClassSubject, isPresent());

              ClassSubject cClassSubject = inspector.clazz(C.class);
              assertThat(cClassSubject, isPresent());

              ClassSubject mainClassSubject = inspector.clazz(Main.class);
              assertThat(mainClassSubject, isPresent());

              // Verify that we have strengthened the type of field fieldWithNullableB to type B.
              FieldSubject fieldWithNullableBSubject =
                  mainClassSubject.uniqueFieldWithOriginalName("fieldWithNullableB");
              assertThat(fieldWithNullableBSubject, isPresent());
              assertEquals(bClassSubject.asTypeSubject(), fieldWithNullableBSubject.getType());

              // Verify that we have strengthened the type of field fieldWithNullableC to type C.
              FieldSubject fieldWithNullableCSubject =
                  mainClassSubject.uniqueFieldWithOriginalName("fieldWithNullableC");
              assertThat(fieldWithNullableCSubject, isPresent());
              // TODO(b/296030319): Should be C.
              assertNotEquals(aClassSubject.asTypeSubject(), fieldWithNullableCSubject.getType());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("B", "C");
  }

  static class Main {

    static A fieldWithNullableB;
    static A fieldWithNullableC;

    public static void main(String[] args) {
      B nullableB = System.currentTimeMillis() > 0 ? new B() : null;
      setFieldWithNullableB(nullableB);
      getFieldWithNullableB();

      B nullableC = System.currentTimeMillis() > 0 ? new C() : null;
      setFieldWithNullableC(nullableC);
      getFieldWithNullableC();
    }

    @NeverInline
    static void setFieldWithNullableB(B b) {
      fieldWithNullableB = b;
    }

    @NeverInline
    static void setFieldWithNullableC(B b) {
      fieldWithNullableC = b;
    }

    @NeverInline
    static void getFieldWithNullableB() {
      System.out.println(fieldWithNullableB);
    }

    @NeverInline
    static void getFieldWithNullableC() {
      System.out.println(fieldWithNullableC);
    }
  }

  @NoVerticalClassMerging
  static class A {}

  @NoVerticalClassMerging
  static class B extends A {

    @Override
    public String toString() {
      return "B";
    }
  }

  static class C extends B {

    @Override
    public String toString() {
      return "C";
    }
  }
}
