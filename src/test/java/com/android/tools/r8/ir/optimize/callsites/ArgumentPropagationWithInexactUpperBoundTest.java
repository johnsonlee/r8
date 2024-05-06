// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.callsites;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoUnusedInterfaceRemoval;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ArgumentPropagationWithInexactUpperBoundTest extends TestBase {

  @Parameter(0)
  public boolean enableDevirtualization;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, devirtualize: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addOptionsModification(options -> options.enableDevirtualization = enableDevirtualization)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoUnusedInterfaceRemovalAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject iClassSubject = inspector.clazz(I.class);
              assertThat(iClassSubject, isPresent());

              MethodSubject iMethodSubject = iClassSubject.uniqueMethodWithOriginalName("m");
              assertThat(iMethodSubject, isPresent());

              ClassSubject aClassSubject = inspector.clazz(A.class);
              assertThat(aClassSubject, isPresent());

              MethodSubject aMethodSubject = aClassSubject.uniqueMethodWithOriginalName("m");
              assertThat(aMethodSubject, isPresent());
              assertTrue(aMethodSubject.streamInstructions().anyMatch(i -> i.isConstNumber(0)));

              ClassSubject cClassSubject = inspector.clazz(C.class);
              assertThat(cClassSubject, isPresent());

              MethodSubject cMethodSubject = cClassSubject.uniqueMethodWithOriginalName("m");
              assertThat(cMethodSubject, isPresent());
              assertTrue(cMethodSubject.streamInstructions().anyMatch(i -> i.isConstNumber(0)));

              ClassSubject dClassSubject = inspector.clazz(D.class);
              assertThat(dClassSubject, isPresent());

              MethodSubject dMethodSubject = dClassSubject.uniqueMethodWithOriginalName("m");
              assertThat(dMethodSubject, isPresent());
              assertTrue(dMethodSubject.streamInstructions().anyMatch(i -> i.isConstNumber(1)));

              ClassSubject eClassSubject = inspector.clazz(E.class);
              assertThat(eClassSubject, isPresent());

              MethodSubject eMethodSubject = eClassSubject.uniqueMethodWithOriginalName("m");
              assertThat(eMethodSubject, isPresent());
              assertTrue(eMethodSubject.streamInstructions().anyMatch(i -> i.isConstNumber(2)));

              ClassSubject mainClassSubject = inspector.clazz(Main.class);
              assertThat(mainClassSubject, isPresent());

              // When devirtualization is enabled we could in principle rewrite the call to I.m() to
              // have symbolic reference A.m.
              MethodSubject mainMethodSubject = mainClassSubject.mainMethod();
              assertThat(mainMethodSubject, isPresent());
              assertThat(mainMethodSubject, invokesMethod(iMethodSubject));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("0", "1", "2");
  }

  static class Main {

    public static void main(String[] args) {
      // Call I.m() with upper bound "A{I}".
      I i = System.currentTimeMillis() > 0 ? new B() : new C();
      i.m(0);

      // Since D does not implement I, the argument 0 should not be propagated to D.m.
      new D().m(1);

      // Since E does not extend A, the argument 0 should not be propagated to E.m.
      new E().m(2);
    }
  }

  @NoUnusedInterfaceRemoval
  interface I {

    void m(int i);
  }

  abstract static class A {

    public void m(int i) {
      System.out.println(i);
    }
  }

  @NoHorizontalClassMerging
  static class B extends A implements I {}

  @NoHorizontalClassMerging
  static class C extends A implements I {

    @Override
    public void m(int i) {
      System.out.println(i);
    }
  }

  // So that calls to A.m() do not have a single target.
  @NeverClassInline
  @NoHorizontalClassMerging
  static class D extends A {

    @NeverInline
    @Override
    public void m(int i) {
      System.out.println(i);
    }
  }

  // So that calls to I.m() do not have a single target.
  @NeverClassInline
  @NoHorizontalClassMerging
  static class E implements I {

    @NeverInline
    @Override
    public void m(int i) {
      System.out.println(i);
    }
  }
}
