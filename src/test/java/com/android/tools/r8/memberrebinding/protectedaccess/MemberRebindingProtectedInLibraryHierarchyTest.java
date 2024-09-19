// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.memberrebinding.protectedaccess;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.InvokeInstructionSubject;
import java.util.LinkedHashMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

// Test for B/367915233.
@RunWith(Parameterized.class)
public class MemberRebindingProtectedInLibraryHierarchyTest extends TestBase {

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
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT)
        .inspect(
            inspector ->
                assertTrue(
                    inspector
                        .clazz(TestClass.class)
                        .mainMethod()
                        .streamInstructions()
                        .filter(InstructionSubject::isInvokeVirtual)
                        .map(
                            instruction -> ((InvokeInstructionSubject) instruction).invokedMethod())
                        .anyMatch(
                            method ->
                                (parameters.isCfRuntime()
                                        ? method
                                            .getHolderType()
                                            .toSourceString()
                                            .equals("java.util.HashMap")
                                        : method
                                            .getHolderType()
                                            .toSourceString()
                                            .equals("java.lang.Object"))
                                    && method.getName().toString().equals("clone"))));
  }

  static class TestClass {

    public static void main(String[] args) {
      new LinkedHashMap<>().clone();
      System.out.println("Hello, world!");
    }
  }
}
