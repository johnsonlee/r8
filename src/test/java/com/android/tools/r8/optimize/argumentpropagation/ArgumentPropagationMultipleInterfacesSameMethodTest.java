// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.BooleanBox;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ArgumentPropagationMultipleInterfacesSameMethodTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    BooleanBox inspected = new BooleanBox();
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepClassAndMembersRules(Main.class)
        .addArgumentPropagatorCodeScannerResultInspector(
            inspector ->
                inspector
                    .assertHasPolymorphicMethodState(
                        Reference.methodFromMethod(I.class.getDeclaredMethod("m")))
                    .assertHasPolymorphicMethodState(
                        Reference.methodFromMethod(J.class.getDeclaredMethod("m")))
                    .apply(ignore -> inspected.set()))
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("foo", "baz", "foo", "baz");
    assertTrue(inspected.isTrue());
  }

  @NoVerticalClassMerging
  interface I {

    String m();
  }

  @NoVerticalClassMerging
  interface J {

    String m();
  }

  @NoHorizontalClassMerging
  static class A implements I, J {

    @Override
    public String m() {
      return System.currentTimeMillis() > 0 ? "foo" : "bar";
    }
  }

  @NoHorizontalClassMerging
  static class B implements I, J {

    @Override
    public String m() {
      return System.currentTimeMillis() > 0 ? "baz" : "qux";
    }
  }

  static class Main {

    public static void main(String[] args) {
      testI(new A());
      testI(new B());
      testJ(new A());
      testJ(new B());
    }

    static void testI(I i) {
      System.out.println(i.m());
    }

    static void testJ(J j) {
      System.out.println(j.m());
    }
  }
}
