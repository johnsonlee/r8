// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.KeepConstantArguments;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;

public class ConstructorMergingWithArgumentsTest extends HorizontalClassMergingTestBase {
  public ConstructorMergingWithArgumentsTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector.assertIsCompleteMergeGroup(A.class, B.class).assertNoOtherClassesMerged())
        .enableConstantArgumentAnnotations()
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("foo hello", "bar world")
        .inspect(
            codeInspector -> {
              ClassSubject aClassSubject = codeInspector.clazz(A.class);
              assertThat(aClassSubject, isPresent());

              MethodSubject fooInitSubject = aClassSubject.init(String.class.getName());
              assertThat(fooInitSubject, isPresent());
              assertTrue(
                  fooInitSubject
                      .streamInstructions()
                      .anyMatch(instruction -> instruction.isConstString("foo ")));

              MethodSubject barInitSubject = aClassSubject.init(String.class.getName(), "boolean");
              assertThat(barInitSubject, isPresent());
              assertTrue(
                  barInitSubject
                      .streamInstructions()
                      .anyMatch(instruction -> instruction.isConstString("bar ")));
            });
  }

  @NeverClassInline
  public static class A {

    @KeepConstantArguments
    @NeverInline
    public A(String arg) {
      System.out.println("foo " + arg);
    }
  }

  @NeverClassInline
  public static class B {

    @KeepConstantArguments
    @NeverInline
    public B(String arg) {
      System.out.println("bar " + arg);
    }
  }

  public static class Main {
    public static void main(String[] args) {
      A a = new A("hello");
      B b = new B("world");
    }
  }
}
