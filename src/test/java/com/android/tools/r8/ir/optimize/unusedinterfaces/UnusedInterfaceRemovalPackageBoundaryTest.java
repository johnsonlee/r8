// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.unusedinterfaces;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ir.optimize.unusedinterfaces.testclasses.UnusedInterfaceRemovalPackageBoundaryTestClasses;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class UnusedInterfaceRemovalPackageBoundaryTest extends TestBase {

  private static final Class<?> I_CLASS = UnusedInterfaceRemovalPackageBoundaryTestClasses.getI();
  private static final Class<?> J_CLASS = UnusedInterfaceRemovalPackageBoundaryTestClasses.J.class;

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().withPartialCompilation().build();
  }

  public UnusedInterfaceRemovalPackageBoundaryTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass(), UnusedInterfaceRemovalPackageBoundaryTestClasses.class)
        .addKeepMainRule(TestClass.class)
        .addKeepClassRules(I_CLASS)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .compile()
        .inspectIf(
            !parameters.isRandomPartialCompilation(),
            inspector -> {
              ClassSubject iClassSubject = inspector.clazz(I_CLASS);
              assertThat(iClassSubject, isPresent());

              ClassSubject jClassSubject = inspector.clazz(J_CLASS);
              assertThat(jClassSubject, isPresent());

              ClassSubject kClassSubject = inspector.clazz(K.class);
              assertThat(kClassSubject, isAbsent());

              ClassSubject aClassSubject = inspector.clazz(A.class);
              assertThat(aClassSubject, isPresent());
              assertEquals(1, aClassSubject.getDexProgramClass().getInterfaces().size());
              assertEquals(
                  jClassSubject.getDexProgramClass().getType(),
                  aClassSubject.getDexProgramClass().getInterfaces().get(0));
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("A");
  }

  @Test
  public void testIOnClasspath() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addProgramClasses(UnusedInterfaceRemovalPackageBoundaryTestClasses.J.class)
        .addClasspathClasses(UnusedInterfaceRemovalPackageBoundaryTestClasses.getI())
        .addKeepMainRule(TestClass.class)
        // TODO(b/410597153): Repackaging does not account for part of a program package being on
        //  classpath. After fixing this, -dontobfuscate should not be needed.
        .addDontObfuscate()
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .compile()
        .inspectIf(
            !parameters.isRandomPartialCompilation(),
            inspector -> {
              ClassSubject jClassSubject = inspector.clazz(J_CLASS);
              assertThat(jClassSubject, isPresent());

              ClassSubject kClassSubject = inspector.clazz(K.class);
              assertThat(kClassSubject, isAbsent());

              ClassSubject aClassSubject = inspector.clazz(A.class);
              assertThat(aClassSubject, isPresent());
              assertEquals(1, aClassSubject.getDexProgramClass().getInterfaces().size());
              assertEquals(
                  jClassSubject.getDexProgramClass().getType(),
                  aClassSubject.getDexProgramClass().getInterfaces().get(0));
            })
        .addRunClasspathClasses(UnusedInterfaceRemovalPackageBoundaryTestClasses.getI())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("A");
  }

  static class TestClass {

    public static void main(String[] args) {
      new A();
    }
  }

  @NoVerticalClassMerging
  interface K extends UnusedInterfaceRemovalPackageBoundaryTestClasses.J {}

  @NeverClassInline
  static class A implements K {

    @NeverInline
    A() {
      System.out.println("A");
    }
  }
}
