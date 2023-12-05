// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.numberunboxing;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CannotUnboxNumberUnboxingTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public CannotUnboxNumberUnboxingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testNumberUnboxing() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .addOptionsModification(opt -> opt.testing.enableNumberUnboxer = true)
        .setMinApi(parameters)
        .compile()
        .inspect(this::assertUnboxing)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("null", "2", "2", "1", "null", "2", "1");
  }

  private void assertUnboxing(CodeInspector codeInspector) {
    ClassSubject mainClass = codeInspector.clazz(Main.class);
    assertThat(mainClass, isPresent());

    MethodSubject methodSubject = mainClass.uniqueMethodWithOriginalName("print");
    assertThat(methodSubject, isPresent());
    assertEquals("java.lang.Integer", methodSubject.getOriginalSignature().parameters[0]);
    assertEquals(
        "java.lang.Integer", methodSubject.getFinalSignature().asMethodSignature().parameters[0]);
  }

  static class Main {

    public static void main(String[] args) {
      cannotUnboxPrint();
      depsNonUnboxable();
    }

    @NeverInline
    private static void depsNonUnboxable() {
      try {
        forward(null);
      } catch (NullPointerException npe) {
        System.out.println("null");
      }
      forward(1);
      forward(0);
    }

    @NeverInline
    private static void forward(Integer i) {
      // Here print2 will get i as a deps which is non-unboxable.
      print2(i);
    }

    @NeverInline
    private static void print2(Integer boxed) {
      System.out.println(boxed + 1);
    }

    @NeverInline
    private static void cannotUnboxPrint() {
      try {
        print(System.currentTimeMillis() > 0 ? null : -1);
      } catch (NullPointerException npe) {
        System.out.println("null");
      }
      print(System.currentTimeMillis() > 0 ? 1 : 0);
      print(1);
      print(0);
    }

    @NeverInline
    private static void print(Integer boxed) {
      System.out.println(boxed + 1);
    }
  }
}
