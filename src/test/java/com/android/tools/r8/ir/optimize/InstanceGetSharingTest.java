// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoMethodStaticizing;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InstanceGetSharingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoMethodStaticizingAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              assertEquals(
                  1,
                  inspector
                      .clazz(A.class)
                      .uniqueMethodWithOriginalName("foo")
                      .streamInstructions()
                      .filter(InstructionSubject::isInstanceGet)
                      .count());
              assertEquals(
                  1,
                  inspector
                      .clazz(C.class)
                      .uniqueMethodWithOriginalName("bar")
                      .streamInstructions()
                      .filter(InstructionSubject::isInstanceGet)
                      .count());
              assertEquals(
                  2,
                  inspector
                      .clazz(C.class)
                      .uniqueMethodWithOriginalName("baz")
                      .streamInstructions()
                      .filter(InstructionSubject::isInstanceGet)
                      .count());
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput("truetruetrue");
  }

  static class TestClass {
    public static void main(String[] args) {
      System.out.print(new A().foo() > 0);
      System.out.print(new C().bar() > 0);
      System.out.print(new C().baz() > 0);
    }
  }

  @NeverClassInline
  static class A {
    private B b = new B();

    @NeverInline
    public long foo() {
      if (System.currentTimeMillis() > 0) {
        return b.getNum() + 1;
      } else {
        return b.getNum() + 2;
      }
    }
  }

  @NoHorizontalClassMerging
  @NeverClassInline
  static class C {

    @NeverInline
    public long bar() {
      long num;
      if (System.currentTimeMillis() > 0) {
        B b1 = new B();
        num = b1.num;
      } else {
        B b2 = new B();
        num = b2.num;
      }
      return num + 1;
    }

    // repro of b/454444103
    @NeverInline
    public long baz() {
      long num;
      B b1 = new B();
      B b2 = new B();
      B someb;
      if (System.currentTimeMillis() > 0) {
        someb = b1;
      } else {
        someb = b2;
      }
      if (System.currentTimeMillis() > 0) {
        num = b1.num;
        someb.num = -1;
      } else {
        num = b2.num;
        someb.num = -1;
      }
      System.out.print("");
      return num;
    }
  }

  @NeverClassInline
  static class B {
    public long num = System.currentTimeMillis();

    @NeverInline
    @NoMethodStaticizing
    long getNum() {
      return num;
    }
  }
}
