// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Regress136250031 extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return TestParameters.builder()
        .withCfRuntimes()
        .withDefaultDexRuntime()
        .withMaximumApiLevel()
        .build();
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(Regress136250031.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("42");
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .addKeepClassAndMembersRules(B.class)
        .addVerticallyMergedClassesInspector(
            inspector -> inspector.assertMergedIntoSubtype(A.class).assertNoOtherClassesMerged())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("42")
        .inspect(
            inspector -> {
              MethodSubject initMethod = inspector.clazz(B.class).init();
              assertTrue(
                  "Expected 'throw null' in " + initMethod.getMethod().codeToString(),
                  initMethod.streamInstructions().anyMatch(InstructionSubject::isThrow));
            });
  }

  static class TestClass {

    // This method is inlined into the constructor for B between entry and the subsequent <init>
    // on Object (after vertically merging A into B).
    public static final String maybeAbort() {
      if (System.currentTimeMillis() < 0) {
        throw null;
      }
      return "42";
    }

    public static void main(String[] args) {
      new B();
    }
  }

  public static class A {
    A(String s) {
      System.out.println(s);
    }
  }

  public static class B extends A {
    B() {
      // The merge and inlining will result in a jump to an non-normal exit.
      // The frame must retain the uninitialized this.
      super(TestClass.maybeAbort());
    }
  }
}
