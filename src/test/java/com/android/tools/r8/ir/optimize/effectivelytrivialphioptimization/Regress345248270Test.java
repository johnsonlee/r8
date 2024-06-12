// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.effectivelytrivialphioptimization;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ir.optimize.effectivelytrivialphioptimization.b345248270.I;
import com.android.tools.r8.ir.optimize.effectivelytrivialphioptimization.b345248270.PublicAccessor;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Regress345248270Test extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static final String EXPECTED_OUTPUT = StringUtils.lines("Hello, world!");

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addProgramClasses(
            I.class, PublicAccessor.class, PublicAccessor.getPackagePrivateImplementationClass())
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    try {
      testForR8(parameters.getBackend())
          .addInnerClasses(getClass())
          .addProgramClasses(
              I.class, PublicAccessor.class, PublicAccessor.getPackagePrivateImplementationClass())
          .addKeepMainRule(TestClass.class)
          .setMinApi(parameters.getApiLevel())
          .enableNeverClassInliningAnnotations()
          .enableNoAccessModificationAnnotationsForMembers()
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutput(EXPECTED_OUTPUT);
      fail("Expected exception");
    } catch (CompilationFailedException e) {
      // TODO(b/345248270): Should not hit an assertion.
      assertTrue(e.getCause() instanceof AssertionError);
      return;
    }
    fail("Expected CompilationFailedException");
  }

  static class TestClass {
    public static I test() {
      I r = null;
      if (System.currentTimeMillis() > 0) {
        r = PublicAccessor.getPackagePrivateImplementation();
      } else {
        System.out.println("Do something");
        r = PublicAccessor.getPackagePrivateImplementation();
      }
      return r;
    }

    public static void main(String[] args) {
      test();
      System.out.println("Hello, world!");
    }
  }
}
