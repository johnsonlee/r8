// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.internal.proto;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

// TODO(b/339100248): Rename class to ProtoBuilderShrinkingTest.
// TODO(b/339100248): Avoid creating edition2023 messages in proto2 package.
//  Instead move proto2 and proto edition2023 messages to com.android.tools.r8.proto.
@RunWith(Parameterized.class)
public class Proto2BuilderShrinkingTest extends ProtoShrinkingTestBase {

  private enum MainClassesConfig {
    ALL,
    BUILDER_WITH_ONE_OF_SETTER_TEST_CLASS,
    BUILDER_WITH_PRIMITIVE_SETTERS_TEST_CLASS,
    BUILDER_WITH_PROTO_BUILDER_SETTER_TEST_CLASS,
    BUILDER_WITH_PROTO_SETTER_TEST_CLASS,
    BUILDER_WITH_REUSED_SETTERS_TEST_CLASS,
    HAS_FLAGGED_OFF_EXTENSION_BUILDER_TEST_CLASS;

    List<String> getMainClasses() {
      switch (this) {
        case ALL:
          return ImmutableList.of(
              "proto2.BuilderWithOneofSetterTestClass",
              "proto2.BuilderWithPrimitiveSettersTestClass",
              "proto2.BuilderWithProtoBuilderSetterTestClass",
              "proto2.BuilderWithProtoSetterTestClass",
              "proto2.BuilderWithReusedSettersTestClass",
              "proto2.HasFlaggedOffExtensionBuilderTestClass");
        case BUILDER_WITH_ONE_OF_SETTER_TEST_CLASS:
          return ImmutableList.of("proto2.BuilderWithOneofSetterTestClass");
        case BUILDER_WITH_PRIMITIVE_SETTERS_TEST_CLASS:
          return ImmutableList.of("proto2.BuilderWithPrimitiveSettersTestClass");
        case BUILDER_WITH_PROTO_BUILDER_SETTER_TEST_CLASS:
          return ImmutableList.of("proto2.BuilderWithProtoBuilderSetterTestClass");
        case BUILDER_WITH_PROTO_SETTER_TEST_CLASS:
          return ImmutableList.of("proto2.BuilderWithProtoSetterTestClass");
        case BUILDER_WITH_REUSED_SETTERS_TEST_CLASS:
          return ImmutableList.of("proto2.BuilderWithReusedSettersTestClass");
        case HAS_FLAGGED_OFF_EXTENSION_BUILDER_TEST_CLASS:
          return ImmutableList.of("proto2.HasFlaggedOffExtensionBuilderTestClass");
        default:
          throw new Unreachable();
      }
    }
  }

  private static final String METHOD_TO_INVOKE_ENUM =
      "com.google.protobuf.GeneratedMessageLite$MethodToInvoke";

  @Parameter(0)
  public MainClassesConfig config;

  @Parameter(1)
  public TestParameters parameters;

  @Parameter(2)
  public ProtoRuntime protoRuntime;

  @Parameter(3)
  public ProtoTestSources protoTestSources;

  @Parameters(name = "{1}, {2}, {3}, {0}")
  public static List<Object[]> data() {
    return buildParameters(
        MainClassesConfig.values(),
        getTestParameters().withDefaultDexRuntime().withAllApiLevels().build(),
        ProtoRuntime.values(),
        ProtoTestSources.getEdition2023AndProto2());
  }

  @Test
  public void testD8() throws Exception {
    protoRuntime.assumeIsNewerThanOrEqualToMinimumRequiredRuntime(protoTestSources);
    D8TestCompileResult compileResult =
        testForD8()
            .addProgramFiles(protoRuntime.getProgramFiles())
            .addProgramFiles(protoTestSources.getProgramFiles())
            .setMinApi(parameters)
            .compile();

    for (String main : config.getMainClasses()) {
      compileResult
          .run(parameters.getRuntime(), main)
          .apply(runResult -> checkRunResult(runResult, main, false));
    }
  }

  @Test
  public void testR8() throws Exception {
    protoRuntime.assumeIsNewerThanOrEqualToMinimumRequiredRuntime(protoTestSources);
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .apply(protoRuntime::addRuntime)
            .apply(protoRuntime::workaroundProtoMessageRemoval)
            .addProgramFiles(protoTestSources.getProgramFiles())
            .addKeepMainRules(config.getMainClasses())
            .allowAccessModification()
            .allowDiagnosticMessages()
            .allowUnusedDontWarnPatterns()
            .allowUnusedProguardConfigurationRules()
            .enableInliningAnnotations()
            .enableProtoShrinking()
            .setMinApi(parameters)
            .compile()
            .assertAllInfoMessagesMatch(
                containsString("Proguard configuration rule does not match anything"))
            .apply(this::inspectWarningMessages)
            .inspect(this::inspect);

    for (String main : config.getMainClasses()) {
      compileResult
          .run(parameters.getRuntime(), main)
          .apply(runResult -> checkRunResult(runResult, main, true));
    }
  }

  private void checkRunResult(SingleTestRunResult<?> runResult, String main, boolean isR8) {
    if (main.equals("proto2.HasFlaggedOffExtensionBuilderTestClass")) {
      runResult.applyIf(
          isR8 && protoRuntime.isEdition2023() && parameters.getApiLevel() == AndroidApiLevel.O,
          rr -> rr.assertFailureWithErrorThatThrows(IllegalAccessException.class),
          !isR8 && protoRuntime.isEdition2023() && protoTestSources.isProto2(),
          rr -> rr.assertFailureWithErrorThatThrows(InvalidProtocolBufferException.class),
          rr -> rr.assertSuccessWithOutput(getExpectedOutput(main)));
    } else {
      runResult.applyIf(
          protoTestSources.getCorrespondingRuntime() == protoRuntime,
          rr -> rr.assertSuccessWithOutput(getExpectedOutput(main)),
          main.equals("proto2.BuilderWithProtoBuilderSetterTestClass")
              || main.equals("proto2.BuilderWithProtoSetterTestClass"),
          rr -> rr.assertFailureWithErrorThatThrows(StringIndexOutOfBoundsException.class),
          rr -> rr.assertFailureWithErrorThatThrows(ArrayIndexOutOfBoundsException.class));
    }
  }

  private static String getExpectedOutput(String main) {
    switch (main) {
      case "proto2.BuilderWithOneofSetterTestClass":
        return StringUtils.lines(
            "builderWithOneofSetter",
            "false",
            "0",
            "true",
            "foo",
            "false",
            "0",
            "false",
            "0",
            "false",
            "");
      case "proto2.BuilderWithPrimitiveSettersTestClass":
        return StringUtils.lines(
            "builderWithPrimitiveSetters",
            "true",
            "17",
            "false",
            "",
            "false",
            "0",
            "false",
            "0",
            "false",
            "",
            "false",
            "0",
            "false",
            "",
            "false",
            "0",
            "true",
            "16",
            "false",
            "");
      case "proto2.BuilderWithProtoBuilderSetterTestClass":
        return StringUtils.lines("builderWithProtoBuilderSetter", "42");
      case "proto2.BuilderWithProtoSetterTestClass":
        return StringUtils.lines("builderWithProtoSetter", "42");
      case "proto2.BuilderWithReusedSettersTestClass":
        return StringUtils.lines(
            "builderWithReusedSetters",
            "true",
            "1",
            "false",
            "",
            "false",
            "0",
            "false",
            "0",
            "false",
            "",
            "true",
            "1",
            "false",
            "",
            "false",
            "0",
            "false",
            "0",
            "true",
            "qux");
      case "proto2.HasFlaggedOffExtensionBuilderTestClass":
        return StringUtils.lines("4");
      default:
        throw new Unreachable();
    }
  }

  private void inspect(CodeInspector outputInspector) {
    verifyBuildersAreAbsent(outputInspector);
    verifyMethodToInvokeValuesAreAbsent(outputInspector);
  }

  private void verifyBuildersAreAbsent(CodeInspector outputInspector) {
    // TODO(b/171441793): Should be optimized out but fails do to soft pinning of super class.
    if (true) {
      return;
    }
    assertThat(
        outputInspector.clazz(
            "com.android.tools.r8.proto2.Shrinking$HasFlaggedOffExtension$Builder"),
        isAbsent());
    assertThat(
        outputInspector.clazz("com.android.tools.r8.proto2.TestProto$Primitives$Builder"),
        isAbsent());
    assertThat(
        outputInspector.clazz("com.android.tools.r8.proto2.TestProto$OuterMessage$Builder"),
        isAbsent());
    assertThat(
        outputInspector.clazz("com.android.tools.r8.proto2.TestProto$NestedMessage$Builder"),
        isAbsent());
  }

  private void verifyMethodToInvokeValuesAreAbsent(CodeInspector outputInspector) {
    ClassSubject generatedMessageLiteClassSubject =
        outputInspector.clazz("com.google.protobuf.GeneratedMessageLite");
    assertThat(generatedMessageLiteClassSubject, isPresent());

    MethodSubject isInitializedMethodSubject =
        generatedMessageLiteClassSubject.uniqueMethodWithOriginalName("isInitialized");

    DexType methodToInvokeType =
        outputInspector.clazz(METHOD_TO_INVOKE_ENUM).getDexProgramClass().getType();
    for (String main : config.getMainClasses()) {
      MethodSubject mainMethodSubject = outputInspector.clazz(main).mainMethod();
      assertThat(mainMethodSubject, isPresent());

      // Verify that the calls to GeneratedMessageLite.createBuilder() have been inlined.
      // TODO(b/339100248): Investigate inadequate inlining with edition2023.
      if (protoRuntime.isLegacy()) {
        assertTrue(
            mainMethodSubject
                .streamInstructions()
                .filter(InstructionSubject::isInvoke)
                .map(InstructionSubject::getMethod)
                .allMatch(
                    method ->
                        method.getHolderType()
                                != generatedMessageLiteClassSubject.getDexProgramClass().getType()
                            || (isInitializedMethodSubject.isPresent()
                                && method
                                    == isInitializedMethodSubject
                                        .getProgramMethod()
                                        .getReference())));
      }

      // Verify that there are no accesses to MethodToInvoke after inlining createBuilder() -- and
      // specifically no accesses to MethodToInvoke.NEW_BUILDER.
      assertTrue(
          main,
          mainMethodSubject
              .streamInstructions()
              .filter(InstructionSubject::isStaticGet)
              .map(instruction -> instruction.getField().getType())
              .noneMatch(methodToInvokeType::equals));

      // Verify that there is no switches on the ordinal of a MethodToInvoke instance.
      assertTrue(mainMethodSubject.streamInstructions().noneMatch(InstructionSubject::isSwitch));
    }
  }
}
