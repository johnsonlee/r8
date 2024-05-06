// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Identical to FieldWithUnknownDynamicTypeIntoParameterTest except that the field {@link Main#f} is
 * explicitly assigned a nullable value.
 */
@RunWith(Parameterized.class)
public class FieldWithUnknownDynamicTypeIntoParameterMaybeNullTest extends TestBase {

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

              ClassSubject mainClassSubject = inspector.clazz(Main.class);
              assertThat(mainClassSubject, isPresent());

              MethodSubject testMethodSubject =
                  mainClassSubject.uniqueMethodWithOriginalName("test");
              assertThat(testMethodSubject, isPresent());
              assertEquals(bClassSubject.asTypeSubject(), testMethodSubject.getParameter(0));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("B");
  }

  static class Main {

    static B f;

    public static void main(String[] args) {
      setField();
      test(f);
    }

    @NeverInline
    static void setField() {
      f = System.currentTimeMillis() > 0 ? new B() : null;
    }

    @NeverInline
    static void test(A a) {
      System.out.println(a);
    }
  }

  @NoVerticalClassMerging
  static class A {}

  static class B extends A {

    @Override
    public String toString() {
      return "B";
    }
  }
}
