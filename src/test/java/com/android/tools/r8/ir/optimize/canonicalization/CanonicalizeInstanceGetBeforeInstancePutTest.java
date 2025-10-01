// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.canonicalization;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NoRedundantFieldLoadElimination;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CanonicalizeInstanceGetBeforeInstancePutTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().withPartialCompilation().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableNeverClassInliningAnnotations()
        .enableNoRedundantFieldLoadEliminationAnnotations()
        .compile()
        .inspectIf(!parameters.isRandomPartialCompilation(), this::inspect)
        .run(parameters.getRuntime(), Main.class, "Hello, world!")
        .assertSuccessWithOutputLines("Hello, world!");
  }

  private void inspect(CodeInspector inspector) {
    MethodSubject mainMethodSubject = inspector.clazz(Main.class).mainMethod();
    assertThat(mainMethodSubject, isPresent());

    assertEquals(
        1,
        mainMethodSubject.streamInstructions().filter(InstructionSubject::isInstanceGet).count());
  }

  static class Main {

    public static void main(String[] args) {
      A a = new A(args[0]);
      if (System.currentTimeMillis() > 0) {
        System.out.println(a.f);
      } else {
        System.out.println(a.f);
      }
    }
  }

  @NeverClassInline
  static class A {

    @NoRedundantFieldLoadElimination final String f;

    A(String f) {
      this.f = f;
    }
  }
}
