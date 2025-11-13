// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.lambdas;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LambdaNamingConflictTest extends TestBase {

  private static final String EXPECTED = StringUtils.lines("boo!");

  private static ClassReference conflictingName;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withAllRuntimes()
        .withApiLevel(AndroidApiLevel.B)
        .enableApiLevelsForCf()
        .build();
  }

  @Parameter(0)
  public TestParameters parameters;

  @BeforeClass
  public static void beforeClass() throws Exception {
    testForD8(getStaticTemp(), Backend.DEX)
        .addInnerClasses(LambdaNamingConflictTest.class)
        .collectSyntheticItems()
        .release()
        .setMinApi(AndroidApiLevel.B)
        .compile()
        .inspectWithSyntheticItems(
            (inspector, syntheticItems) ->
                conflictingName = syntheticItems.syntheticLambdaClass(TestClass.class, 0));
    assertNotNull(conflictingName);
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(I.class)
        .addProgramClassFileData(getConflictingNameClass())
        .addProgramClassFileData(getTransformedMainClass())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addProgramClasses(I.class)
        .addProgramClassFileData(getConflictingNameClass())
        .addProgramClassFileData(getTransformedMainClass())
        .collectSyntheticItems()
        .setMinApi(parameters)
        .compile()
        .inspectWithSyntheticItems(
            (inspector, syntheticItems) -> {
              if (parameters.isDexRuntime()) {
                assertNull(syntheticItems.syntheticLambdaClass(TestClass.class, 0));
                assertNotNull(syntheticItems.syntheticLambdaClass(TestClass.class, 1));
              }
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(I.class)
        .addProgramClassFileData(getConflictingNameClass())
        .addProgramClassFileData(getTransformedMainClass())
        .collectSyntheticItems()
        .setMinApi(parameters)
        .addKeepMainRule(TestClass.class)
        // Ensure that R8 cannot remove or rename the conflicting name.
        .addKeepClassAndMembersRules(conflictingName.getTypeName())
        .compile()
        .inspectWithSyntheticItems(
            (inspector, syntheticItems) -> {
              if (parameters.isDexRuntime()) {
                assertNull(syntheticItems.syntheticLambdaClass(TestClass.class, 0));
                assertNotNull(syntheticItems.syntheticLambdaClass(TestClass.class, 1));
              }
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  private byte[] getTransformedMainClass() throws Exception {
    return transformer(TestClass.class)
        .transformMethodInsnInMethod(
            "main",
            (opcode, owner, name, descriptor, isInterface, visitor) ->
                visitor.visitMethodInsn(
                    opcode, conflictingName.getBinaryName(), name, descriptor, isInterface))
        .transform();
  }

  private byte[] getConflictingNameClass() throws Exception {
    return transformer(WillBeConflictingName.class)
        .setClassDescriptor(conflictingName.getDescriptor())
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
