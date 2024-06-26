// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
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
public class NonReboundFieldReferenceCollisionTest extends TestBase {

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
        .addHorizontallyMergedClassesInspector(
            inspector -> inspector.assertMergedInto(C.class, B.class).assertNoOtherClassesMerged())
        // The minifier may assign different names to A.f and B.f, so disable obfuscation so that we
        // can check that the two fields have the same name through out the compilation.
        .addDontObfuscate()
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject aClassSubject = inspector.clazz(A.class);
              assertThat(aClassSubject, isPresent());

              FieldSubject shadowedFieldSubject = aClassSubject.uniqueFieldWithOriginalName("f");
              assertThat(shadowedFieldSubject, isPresent());

              ClassSubject bClassSubject = inspector.clazz(B.class);
              assertThat(bClassSubject, isPresent());

              FieldSubject shadowingFieldSubject = bClassSubject.uniqueFieldWithOriginalName("f");
              assertThat(shadowingFieldSubject, isPresent());
              assertEquals(
                  shadowingFieldSubject.getFinalName(), shadowedFieldSubject.getFinalName());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("1", "2", "3", "4");
  }

  static class Main {

    public static void main(String[] args) {
      B b = System.currentTimeMillis() > 0 ? new B(one(), two()) : new B(three(), four());
      System.out.println(b.f());
      System.out.println(b.f);
      C c = System.currentTimeMillis() > 0 ? new C(three(), four()) : new C(one(), two());
      System.out.println(c.f);
      System.out.println(c.g);
    }

    static int one() {
      return 1;
    }

    static int two() {
      return 2;
    }

    static int three() {
      return 3;
    }

    static int four() {
      return 4;
    }
  }

  @NoVerticalClassMerging
  abstract static class A {

    int f;

    @NeverInline
    A(int f) {
      this.f = f;
    }

    int f() {
      return f;
    }
  }

  @NeverClassInline
  static class B extends A {

    int f;

    @NeverInline
    B(int f, int g) {
      super(f);
      this.f = g;
    }
  }

  @NeverClassInline
  static class C extends A {

    int g;

    @NeverInline
    C(int f, int g) {
      super(f);
      this.g = g;
    }
  }
}
