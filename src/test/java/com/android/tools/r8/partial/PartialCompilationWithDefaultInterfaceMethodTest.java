// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import static com.android.tools.r8.ir.desugar.itf.InterfaceDesugaringSyntheticHelper.DEFAULT_METHOD_PREFIX;
import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbstract;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PartialCompilationWithDefaultInterfaceMethodTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesAndAllApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8Partial(parameters.getBackend())
        .addR8IncludedClasses(I.class, J.class)
        .addR8ExcludedClasses(Main.class, A.class)
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              if (parameters.canUseDefaultAndStaticInterfaceMethods()) {
                // Verify that the default interface method was kept.
                ClassSubject jClassSubject = inspector.clazz(J.class);
                assertThat(jClassSubject, isPresent());

                MethodSubject jMethodSubject = jClassSubject.uniqueMethodWithOriginalName("m");
                assertThat(jMethodSubject, isPresent());
                assertThat(jMethodSubject, not(isAbstract()));
              } else {
                // Verify that a bridge was inserted in the D8 part that calls the companion class
                // method in the R8 part.
                ClassSubject jCompanionClassSubject =
                    inspector.clazz(SyntheticItemsTestUtils.syntheticCompanionClass(J.class));
                assertThat(jCompanionClassSubject, isPresent());

                MethodSubject jCompanionMethodSubject =
                    jCompanionClassSubject.uniqueMethodWithOriginalName(
                        DEFAULT_METHOD_PREFIX + "m");
                assertThat(jCompanionMethodSubject, isPresent());

                ClassSubject aClassSubject = inspector.clazz(A.class);
                assertThat(aClassSubject, isPresent());

                MethodSubject aMethodSubject = aClassSubject.uniqueMethodWithOriginalName("m");
                assertThat(aMethodSubject, isPresent());
                assertThat(aMethodSubject, invokesMethod(jCompanionMethodSubject));
              }
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  // D8

  static class Main {

    public static void main(String[] args) {
      I i = new A();
      i.m();
    }
  }

  static class A implements J {}

  // R8

  interface I {

    void m();
  }

  interface J extends I {

    @Override
    default void m() {
      System.out.println("Hello, world!");
    }
  }
}
