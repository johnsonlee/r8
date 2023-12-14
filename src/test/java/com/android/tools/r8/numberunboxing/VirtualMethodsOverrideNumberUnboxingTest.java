// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.numberunboxing;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class VirtualMethodsOverrideNumberUnboxingTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public VirtualMethodsOverrideNumberUnboxingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testNumberUnboxing() throws Throwable {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNeverClassInliningAnnotations()
        .addOptionsModification(opt -> opt.testing.enableNumberUnboxer = true)
        .setMinApi(parameters)
        .compile()
        .inspect(this::assertUnboxing)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("3", "1", "43", "5", "0", "43");
  }

  private void assertUnboxed(MethodSubject methodSubject) {
    assertThat(methodSubject, isPresent());
    assertTrue(methodSubject.getProgramMethod().getParameter(0).isDoubleType());
    assertTrue(methodSubject.getProgramMethod().getParameter(1).isIntType());
    assertTrue(methodSubject.getProgramMethod().getReturnType().isLongType());
  }

  private void assertUnboxing(CodeInspector codeInspector) {
    codeInspector.forAllClasses(c -> c.forAllVirtualMethods(this::assertUnboxed));
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println(new Add().convert(1.3, 1) + 1L);
      System.out.println(new Sub().convert(1.4, 2) + 1L);
      System.out.println(new Cst().convert(1.4, 2) + 1L);
      run(new Add());
      run(new Sub());
      run(new Cst());
    }

    @NeverInline
    private static void run(Top top) {
      System.out.println(top.convert(1.5, 3) + 1L);
    }
  }

  @NeverClassInline
  interface Top {
    @NeverInline
    Long convert(Double d, Integer i);
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class Add implements Top {
    @Override
    @NeverInline
    public Long convert(Double d, Integer i) {
      return Long.valueOf((long) (d.doubleValue() + i.intValue()));
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class Sub implements Top {
    @Override
    @NeverInline
    public Long convert(Double d, Integer i) {
      return Long.valueOf((long) (d.doubleValue() - i.intValue()));
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class Cst implements Top {
    @Override
    @NeverInline
    public Long convert(Double d, Integer i) {
      return Long.valueOf(42L);
    }
  }
}
