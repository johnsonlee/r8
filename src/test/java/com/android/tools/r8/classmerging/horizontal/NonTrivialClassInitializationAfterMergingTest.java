// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoMethodStaticizing;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NonTrivialClassInitializationAfterMergingTest extends TestBase {

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
        .addKeepClassAndMembersRules(I.class)
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector
                    .applyIf(
                        !parameters.canUseDefaultAndStaticInterfaceMethods(),
                        i -> i.assertIsCompleteMergeGroup(A.class, B.class))
                    .assertNoOtherClassesMerged())
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoMethodStaticizingAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            parameters.canUseDefaultAndStaticInterfaceMethods(),
            runResult -> runResult.assertSuccessWithOutputLines("Hello world!"),
            runResult -> runResult.assertSuccessWithOutput("Hello"));
  }

  static class Main {

    public static void main(String[] args) {
      new A();
      System.out.print("Hello");
      new B();
    }
  }

  public interface I {

    Greeter greeter = new Greeter();

    @NeverInline
    @NoMethodStaticizing
    default void foo() {
      System.out.print("");
    }
  }

  @NeverClassInline
  static class A {

    @NeverInline
    A() {
      System.out.print("");
    }
  }

  // Prints " world!" when initialized due to implementing I.
  @NeverClassInline
  static class B implements I {

    @NeverInline
    B() {
      System.out.print("");
    }
  }

  @NoHorizontalClassMerging
  static class Greeter {

    static {
      System.out.println(" world!");
    }
  }
}
