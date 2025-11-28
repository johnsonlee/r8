// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ProcessKotlinNullChecksTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static final String EXPECTED_OUTPUT_WITH_INVOKE_OF_INTRINSICS =
      StringUtils.lines("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "Hello, world!");
  private static final String EXPECTED_OUTPUT_WITHOUT_INVOKE_OF_INTRINSICS =
      StringUtils.lines("Hello, world!");
  private static final String[] RUN_ARGUMENTS =
      new String[] {"", "", "", "", "", "", "", "", "", ""};

  private void assertKotlinIntrinsicsInvokes(CodeInspector inspector, int count) {
    assertEquals(count, countKotlinIntrinsicsInvokes(inspector));
  }

  private long countKotlinIntrinsicsInvokes(CodeInspector inspector) {
    return inspector
        .clazz(TestClass.class)
        .mainMethod()
        .streamInstructions()
        .filter(InstructionSubject::isInvokeStatic)
        .map(InstructionSubject::getMethod)
        .filter(
            dexMethod ->
                dexMethod
                    .getHolderType()
                    .equals(inspector.getFactory().createType("Lkotlin/jvm/internal/Intrinsics;")))
        .count();
  }

  private void assertObjectGetClassInvokes(CodeInspector inspector, int count) {
    assertEquals(count, countObjectGetClassInvokes(inspector));
  }

  private long countObjectGetClassInvokes(CodeInspector inspector) {
    return inspector
        .clazz(TestClass.class)
        .mainMethod()
        .streamInstructions()
        .filter(InstructionSubject::isInvokeVirtual)
        .map(InstructionSubject::getMethod)
        .filter(
            dexMethod ->
                dexMethod
                    .getHolderType()
                    .isIdenticalTo(inspector.getFactory().createType("Ljava/lang/Object;")))
        .filter(
            dexMethod ->
                dexMethod.getName().isIdenticalTo(inspector.getFactory().createString("getClass")))
        .count();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClassFileData(getTransformedMain(), getTransformedKotlinIntrinsics())
        .run(parameters.getRuntime(), TestClass.class, RUN_ARGUMENTS)
        .inspect(inspector -> assertKotlinIntrinsicsInvokes(inspector, 10))
        .inspect(inspector -> assertObjectGetClassInvokes(inspector, 0))
        .assertSuccessWithOutput(EXPECTED_OUTPUT_WITH_INVOKE_OF_INTRINSICS);
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters)
        .addProgramClassFileData(getTransformedMain(), getTransformedKotlinIntrinsics())
        .run(parameters.getRuntime(), TestClass.class, RUN_ARGUMENTS)
        .inspect(inspector -> assertKotlinIntrinsicsInvokes(inspector, 10))
        .inspect(inspector -> assertObjectGetClassInvokes(inspector, 0))
        .assertSuccessWithOutput(EXPECTED_OUTPUT_WITH_INVOKE_OF_INTRINSICS);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addProgramClassFileData(getTransformedMain())
        .addClasspathClassFileData(getTransformedKotlinIntrinsics())
        .addKeepMainRule(TestClass.class)
        .run(parameters.getRuntime(), TestClass.class, RUN_ARGUMENTS)
        .inspect(inspector -> assertKotlinIntrinsicsInvokes(inspector, 0))
        .inspect(inspector -> assertObjectGetClassInvokes(inspector, 10))
        .assertSuccessWithOutput(EXPECTED_OUTPUT_WITHOUT_INVOKE_OF_INTRINSICS);
  }

  @Test
  public void testR8ProcessKotlinNullChecksLeave() throws Exception {
    Path fakeKotlinIntrinsicsLibrary =
        testForD8(parameters)
            .addProgramClassFileData(getTransformedKotlinIntrinsics())
            .compile()
            .writeToZip();
    testForR8(parameters)
        .addProgramClassFileData(getTransformedMain())
        .addClasspathClassFileData(getTransformedKotlinIntrinsics())
        .addKeepMainRule(TestClass.class)
        .addKeepRules("-processkotlinnullchecks keep")
        .addRunClasspathFiles(fakeKotlinIntrinsicsLibrary)
        .run(parameters.getRuntime(), TestClass.class, RUN_ARGUMENTS)
        .inspect(inspector -> assertKotlinIntrinsicsInvokes(inspector, 10))
        .inspect(inspector -> assertObjectGetClassInvokes(inspector, 0))
        .assertSuccessWithOutput(EXPECTED_OUTPUT_WITH_INVOKE_OF_INTRINSICS);
  }

  @Test
  public void testR8ProcessKotlinNullChecksRemoveMessage() throws Exception {
    testForR8(parameters)
        .addProgramClassFileData(getTransformedMain())
        .addClasspathClassFileData(getTransformedKotlinIntrinsics())
        .addKeepMainRule(TestClass.class)
        .addKeepRules("-processkotlinnullchecks remove_message")
        .run(parameters.getRuntime(), TestClass.class, RUN_ARGUMENTS)
        .inspect(inspector -> assertKotlinIntrinsicsInvokes(inspector, 0))
        .inspect(inspector -> assertObjectGetClassInvokes(inspector, 10))
        .assertSuccessWithOutput(EXPECTED_OUTPUT_WITHOUT_INVOKE_OF_INTRINSICS);
  }

  @Test
  public void testR8ProcessKotlinNullChecksRemove() throws Exception {
    testForR8(parameters)
        .addProgramClassFileData(getTransformedMain())
        .addClasspathClassFileData(getTransformedKotlinIntrinsics())
        .addKeepMainRule(TestClass.class)
        .addKeepRules("-processkotlinnullchecks remove")
        .run(parameters.getRuntime(), TestClass.class, RUN_ARGUMENTS)
        .inspect(inspector -> assertKotlinIntrinsicsInvokes(inspector, 0))
        .inspect(inspector -> assertObjectGetClassInvokes(inspector, 0))
        .assertSuccessWithOutput(EXPECTED_OUTPUT_WITHOUT_INVOKE_OF_INTRINSICS);
  }

  private void addRuleForKotlinIntrinsics(String rule, R8TestBuilder<?, ?, ?> builder) {
    builder.addKeepRules(
        rule + " class kotlin.jvm.internal.Intrinsics {",
        "    void checkNotNull(java.lang.Object);",
        "    void checkNotNull(java.lang.Object, java.lang.String);",
        "    void checkExpressionValueIsNotNull(java.lang.Object, java.lang.String);",
        "    void checkNotNullExpressionValue(java.lang.Object, java.lang.String);",
        "    void checkReturnedValueIsNotNull(java.lang.Object, java.lang.String);",
        "    void checkReturnedValueIsNotNull(java.lang.Object, java.lang.String,"
            + " java.lang.String);",
        "    void checkFieldIsNotNull(java.lang.Object, java.lang.String);",
        "    void checkFieldIsNotNull(java.lang.Object, java.lang.String, java.lang.String);",
        "    void checkParameterIsNotNull(java.lang.Object, java.lang.String);",
        "    void checkNotNullParameter(java.lang.Object, java.lang.String);",
        "}");
  }

  @Test
  public void testR8ConvertCheckNotNull() throws Exception {
    testForR8(parameters)
        .addProgramClassFileData(getTransformedMain())
        .addClasspathClassFileData(getTransformedKotlinIntrinsics())
        .addKeepRules("-processkotlinnullchecks keep")
        .apply(b -> addRuleForKotlinIntrinsics("-convertchecknotnull", b))
        .addKeepMainRule(TestClass.class)
        .run(parameters.getRuntime(), TestClass.class, RUN_ARGUMENTS)
        .inspect(inspector -> assertKotlinIntrinsicsInvokes(inspector, 0))
        .inspect(inspector -> assertObjectGetClassInvokes(inspector, 10))
        .assertSuccessWithOutput(EXPECTED_OUTPUT_WITHOUT_INVOKE_OF_INTRINSICS);
  }

  @Test
  public void testR8AssumeNoSideeffects() throws Exception {
    testForR8(parameters)
        .addProgramClassFileData(getTransformedMain())
        .addClasspathClassFileData(getTransformedKotlinIntrinsics())
        .addKeepRules("-processkotlinnullchecks remove_message")
        .apply(b -> addRuleForKotlinIntrinsics("-assumenosideeffects", b))
        .addKeepMainRule(TestClass.class)
        .run(parameters.getRuntime(), TestClass.class, RUN_ARGUMENTS)
        .inspect(inspector -> assertKotlinIntrinsicsInvokes(inspector, 0))
        .inspect(inspector -> assertObjectGetClassInvokes(inspector, 0))
        .assertSuccessWithOutput(EXPECTED_OUTPUT_WITHOUT_INVOKE_OF_INTRINSICS);
  }

  private byte[] getTransformedMain() throws IOException {
    return transformer(TestClass.class)
        .replaceClassDescriptorInMethodInstructions(
            ImmutableMap.of(
                descriptor(KotlinJvmInternalIntrinsicsStub.class),
                Kotlin.kotlinJvmInternalIntrinsicsDescriptor))
        .transform();
  }

  private byte[] getTransformedKotlinIntrinsics() throws IOException {
    return transformer(KotlinJvmInternalIntrinsicsStub.class)
        .setClassDescriptor(Kotlin.kotlinJvmInternalIntrinsicsDescriptor)
        .transform();
  }

  public static class KotlinJvmInternalIntrinsicsStub {

    public static void checkNotNull(Object o) {
      System.out.println("1");
    }

    public static void checkNotNull(Object o, String s) {
      System.out.println("2");
    }

    public static void checkExpressionValueIsNotNull(Object o, String s) {
      System.out.println("3");
    }

    public static void checkNotNullExpressionValue(Object o, String s) {
      System.out.println("4");
    }

    public static void checkReturnedValueIsNotNull(Object o, String s1, String s2) {
      System.out.println("5");
    }

    public static void checkReturnedValueIsNotNull(Object o, String s) {
      System.out.println("6");
    }

    public static void checkFieldIsNotNull(Object o, String s1, String s2) {
      System.out.println("7");
    }

    public static void checkFieldIsNotNull(Object o, String s) {
      System.out.println("8");
    }

    public static void checkParameterIsNotNull(Object o, String s) {
      System.out.println("9");
    }

    public static void checkNotNullParameter(Object o, String s) {
      System.out.println("10");
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      KotlinJvmInternalIntrinsicsStub.checkNotNull(args[0]);
      KotlinJvmInternalIntrinsicsStub.checkNotNull(args[1], "");
      KotlinJvmInternalIntrinsicsStub.checkExpressionValueIsNotNull(args[2], "");
      KotlinJvmInternalIntrinsicsStub.checkNotNullExpressionValue(args[3], "");
      KotlinJvmInternalIntrinsicsStub.checkReturnedValueIsNotNull(args[4], "", "");
      KotlinJvmInternalIntrinsicsStub.checkReturnedValueIsNotNull(args[5], "");
      KotlinJvmInternalIntrinsicsStub.checkFieldIsNotNull(args[6], "", "");
      KotlinJvmInternalIntrinsicsStub.checkFieldIsNotNull(args[7], "");
      KotlinJvmInternalIntrinsicsStub.checkParameterIsNotNull(args[8], "");
      KotlinJvmInternalIntrinsicsStub.checkNotNullParameter(args[9], "");
      System.out.println("Hello, world!");
    }
  }
}
