// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.lambdas;

import static com.android.tools.r8.synthesis.SyntheticItemsTestUtils.getSyntheticItemsTestUtils;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class LambdaNamingConflictTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("boo!");

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withAllRuntimes()
        .withApiLevel(AndroidApiLevel.B)
        .enableApiLevelsForCf()
        .build();
  }

  // The expected synthetic name is the context of the lambda, TestClass, and the first id.
  private static ClassReference getConflictingName(boolean isR8) {
    return getSyntheticItemsTestUtils(isR8).syntheticLambdaClass(TestClass.class, 0);
  }

  private final TestParameters parameters;

  public LambdaNamingConflictTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(I.class)
        .addProgramClassFileData(getConflictingNameClass(false))
        .addProgramClassFileData(getTransformedMainClass(false))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addProgramClasses(I.class)
        .addProgramClassFileData(getConflictingNameClass(false))
        .addProgramClassFileData(getTransformedMainClass(false))
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(I.class)
        .addProgramClassFileData(getConflictingNameClass(true))
        .addProgramClassFileData(getTransformedMainClass(true))
        .setMinApi(parameters)
        .addKeepMainRule(TestClass.class)
        // Ensure that R8 cannot remove or rename the conflicting name.
        .addKeepClassAndMembersRules(getConflictingName(true).getTypeName())
        .addOptionsModification(
            options -> options.desugarSpecificOptions().minimizeSyntheticNames = true)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  private byte[] getTransformedMainClass(boolean isR8) throws Exception {
    return transformer(TestClass.class)
        .transformMethodInsnInMethod(
            "main",
            (opcode, owner, name, descriptor, isInterface, visitor) ->
                visitor.visitMethodInsn(
                    opcode,
                    getConflictingName(isR8).getBinaryName(),
                    name,
                    descriptor,
                    isInterface))
        .transform();
  }

  private byte[] getConflictingNameClass(boolean isR8) throws Exception {
    return transformer(WillBeConflictingName.class)
        .setClassDescriptor(getConflictingName(isR8).getDescriptor())
        .transform();
  }

  interface I {
    void bar();
  }

  static class WillBeConflictingName {
    public static void foo(I i) {
      i.bar();
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      WillBeConflictingName.foo(() -> System.out.println("boo!"));
    }
  }
}
