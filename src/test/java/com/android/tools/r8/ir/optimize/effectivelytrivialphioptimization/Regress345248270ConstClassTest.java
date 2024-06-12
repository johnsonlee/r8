// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.effectivelytrivialphioptimization;

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
public class Regress345248270ConstClassTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static final String EXPECTED_OUTPUT = StringUtils.lines("true");

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
  }

  static class TestClass {
    public static Class<?> test() {
      Class<?> r = null;
      if (System.currentTimeMillis() > 0) {
        r = PublicAccessor.getPackagePrivateImplementationClass();
      } else {
        System.out.println("Do something");
        r = PublicAccessor.getPackagePrivateImplementationClass();
      }
      return r;
    }

    public static Class<?> test2() {
      return System.currentTimeMillis() > 0
          ? PublicAccessor.getPackagePrivateImplementationClass()
          : null;
    }

    public static void main(String[] args) {
      Class<?> c = test();
      System.out.println(c == test2());
    }
  }
}
