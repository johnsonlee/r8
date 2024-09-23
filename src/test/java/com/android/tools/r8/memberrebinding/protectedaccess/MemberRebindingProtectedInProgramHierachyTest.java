// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.memberrebinding.protectedaccess;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethodWithHolderAndName;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.memberrebinding.protectedaccess.util.A;
import com.android.tools.r8.memberrebinding.protectedaccess.util.B;
import com.android.tools.r8.memberrebinding.protectedaccess.util.Base;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MemberRebindingProtectedInProgramHierachyTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static final String EXPECTED_OUTPUT = StringUtils.lines("Hello, world!");

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Base.class, A.class, B.class, TestClass.class)
        .addKeepMainRule(TestClass.class)
        .addKeepClassAndMembersRules(Base.class, A.class, B.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT)
        .inspect(
            inspector ->
                assertThat(
                    inspector.clazz(TestClass.class).mainMethod(),
                    invokesMethodWithHolderAndName(A.class.getTypeName(), "m")));
  }

  static class TestClass extends Base {
    public static void main(String[] args) {
      // This will be rebound to A.m. If member rebinding is changed to rebind to higher in the
      // hierarchy then this still cannot be rebound to Base.m (at least not for class file output).
      new B().m();
    }
  }
}
