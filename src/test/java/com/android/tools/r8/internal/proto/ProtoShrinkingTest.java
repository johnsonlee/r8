// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal.proto;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsentIf;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.analysis.ProtoApplicationStats;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

// TODO(b/339100248): Avoid creating edition2023 messages in proto2 package.
//  Instead move proto2 and proto edition2023 messages to com.android.tools.r8.proto.
@RunWith(Parameterized.class)
public class ProtoShrinkingTest extends ProtoShrinkingTestBase {

  private static final String CONTAINS_FLAGGED_OFF_FIELD =
      "com.android.tools.r8.proto2.Shrinking$ContainsFlaggedOffField";
  private static final String EXT_B =
      "com.android.tools.r8.proto2.Shrinking$PartiallyUsedWithExtension$ExtB";
  private static final String EXT_C =
      "com.android.tools.r8.proto2.Shrinking$PartiallyUsedWithExtension$ExtC";
  private static final String FLAGGED_OFF_EXTENSION =
      "com.android.tools.r8.proto2.Shrinking$HasFlaggedOffExtension$Ext";
  private static final String HAS_NO_USED_EXTENSIONS =
      "com.android.tools.r8.proto2.Shrinking$HasNoUsedExtensions";
  private static final String HAS_REQUIRED_FIELD =
      "com.android.tools.r8.proto2.Graph$HasRequiredField";
  private static final String PARTIALLY_USED =
      "com.android.tools.r8.proto2.Shrinking$PartiallyUsed";
  private static final String USED_ROOT = "com.android.tools.r8.proto2.Graph$UsedRoot";
  private static final String USED_VIA_HAZZER =
      "com.android.tools.r8.proto2.Shrinking$UsedViaHazzer";
  private static final String USES_ONLY_REPEATED_FIELDS =
      "com.android.tools.r8.proto2.Shrinking$UsesOnlyRepeatedFields";

  private static final String MAIN = "proto2.TestClass";

  @Parameter(0)
  public boolean allowAccessModification;

  @Parameter(1)
  public boolean enableMinification;

  @Parameter(2)
  public TestParameters parameters;

  @Parameter(3)
  public ProtoRuntime protoRuntime;

  @Parameter(4)
  public ProtoTestSources protoTestSources;

  @Parameters(name = "{2}, {3}, {4}, allow access modification: {0}, enable minification: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        BooleanUtils.values(),
        getTestParameters().withDefaultDexRuntime().withAllApiLevels().build(),
        ProtoRuntime.values(),
        ProtoTestSources.getEdition2023AndProto2());
  }

  @Test
  public void testD8() throws Exception {
    protoRuntime.assumeIsNewerThanOrEqualToMinimumRequiredRuntime(protoTestSources);
    testForD8()
        .addProgramFiles(protoRuntime.getProgramFiles())
        .addProgramFiles(protoTestSources.getProgramFiles())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), MAIN)
        .apply(this::checkRunResult);
  }

  @Test
  public void testR8() throws Exception {
    protoRuntime.assumeIsNewerThanOrEqualToMinimumRequiredRuntime(protoTestSources);
    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .apply(protoRuntime::addRuntime)
            .apply(protoRuntime::workaroundProtoMessageRemoval)
            .addProgramFiles(protoTestSources.getProgramFiles())
            .addKeepMainRule(MAIN)
            // TODO(b/173340579): This rule should not be needed to allow shrinking of
            //  PartiallyUsed$Enum.
            .addNoHorizontalClassMergingRule(PARTIALLY_USED + "$Enum$1")
            .allowAccessModification(allowAccessModification)
            .allowDiagnosticMessages()
            .allowUnusedDontWarnPatterns()
            .allowUnusedProguardConfigurationRules()
            .enableProguardTestOptions()
            .enableProtoShrinking()
            .addDontObfuscateUnless(enableMinification)
            .setMinApi(parameters)
            .compile()
            .assertAllInfoMessagesMatch(
                containsString("Proguard configuration rule does not match anything"))
            .apply(this::inspectWarningMessages)
            .inspect(
                outputInspector -> {
                  CodeInspector inputInspector = protoTestSources.getInspector();
                  verifyMapAndRequiredFieldsAreKept(inputInspector, outputInspector);
                  verifyUnusedExtensionsAreRemoved(inputInspector, outputInspector);
                  verifyUnusedFieldsAreRemoved(inputInspector, outputInspector);
                  verifyUnusedHazzerBitFieldsAreRemoved(inputInspector, outputInspector);
                  verifyUnusedTypesAreRemoved(inputInspector, outputInspector);
                })
            .run(parameters.getRuntime(), MAIN)
            .apply(this::checkRunResult);

    if (protoTestSources.getCorrespondingRuntime() != protoRuntime) {
      result.assertFailure();
      return;
    }

    DexItemFactory dexItemFactory = new DexItemFactory();
    ProtoApplicationStats original =
        new ProtoApplicationStats(dexItemFactory, protoTestSources.getInspector());
    ProtoApplicationStats actual =
        new ProtoApplicationStats(dexItemFactory, result.inspector(), original);

    assertEquals(
        ImmutableSet.of(),
        actual.getGeneratedExtensionRegistryStats().getSpuriouslyRetainedExtensionFields());

    if (ToolHelper.isLocalDevelopment()) {
      System.out.println(actual.getStats());
    }
  }

  private void checkRunResult(SingleTestRunResult<?> runResult) {
    runResult.applyIf(
        protoTestSources.getCorrespondingRuntime() == protoRuntime,
        rr ->
            rr.assertSuccessWithOutputLines(
                "--- roundtrip ---",
                "true",
                "123",
                "asdf",
                "9223372036854775807",
                "qwerty",
                "--- partiallyUsed_proto2 ---",
                "true",
                "42",
                "--- usedViaHazzer ---",
                "true",
                "--- usedViaOneofCase ---",
                "true",
                "--- usesOnlyRepeatedFields ---",
                "1",
                "--- containsFlaggedOffField ---",
                "0",
                "--- hasFlaggedOffExtension ---",
                "4",
                "--- useOneExtension ---",
                "42",
                "--- keepMapAndRequiredFields ---",
                "true",
                "10",
                "10",
                "10"),
        rr -> rr.assertFailureWithErrorThatThrows(ArrayIndexOutOfBoundsException.class));
  }

  private void verifyMapAndRequiredFieldsAreKept(
      CodeInspector inputInspector, CodeInspector outputInspector) {
    // Verify the existence of various fields in the input.
    {
      ClassSubject usedRootClassSubject = inputInspector.clazz(USED_ROOT);
      assertThat(usedRootClassSubject, isPresent());
      assertThat(
          usedRootClassSubject.uniqueFieldWithOriginalName("hasRequiredFieldA_"), isPresent());
      assertThat(
          usedRootClassSubject.uniqueFieldWithOriginalName("hasRequiredFieldB_"), isPresent());
      assertThat(usedRootClassSubject.uniqueFieldWithOriginalName("myOneof_"), isPresent());
      assertThat(
          usedRootClassSubject.uniqueFieldWithOriginalName("recursiveWithRequiredField_"),
          isPresent());
      assertThat(
          usedRootClassSubject.uniqueFieldWithOriginalName("isExtendedWithOptional_"), isPresent());
      assertThat(
          usedRootClassSubject.uniqueFieldWithOriginalName("isExtendedWithScalars_"), isPresent());
      assertThat(
          usedRootClassSubject.uniqueFieldWithOriginalName("isExtendedWithRequiredField_"),
          isPresent());
      assertThat(
          usedRootClassSubject.uniqueFieldWithOriginalName(
              "isRepeatedlyExtendedWithRequiredField_"),
          isPresent());
      assertThat(usedRootClassSubject.uniqueFieldWithOriginalName("hasMapField_"), isPresent());

      ClassSubject hasRequiredFieldClassSubject = inputInspector.clazz(HAS_REQUIRED_FIELD);
      assertThat(hasRequiredFieldClassSubject, isPresent());
      assertThat(hasRequiredFieldClassSubject.uniqueFieldWithOriginalName("value_"), isPresent());
    }

    // Verify the existence of various fields in the output.
    {
      ClassSubject usedRootClassSubject = outputInspector.clazz(USED_ROOT);
      assertThat(usedRootClassSubject, isPresent());
      assertThat(
          usedRootClassSubject.uniqueFieldWithOriginalName("hasRequiredFieldA_"), isPresent());
      assertThat(
          usedRootClassSubject.uniqueFieldWithOriginalName("hasRequiredFieldB_"), isPresent());
      assertThat(usedRootClassSubject.uniqueFieldWithOriginalName("myOneof_"), isPresent());
      assertThat(
          usedRootClassSubject.uniqueFieldWithOriginalName("recursiveWithRequiredField_"),
          isPresent());
      assertThat(usedRootClassSubject.uniqueFieldWithOriginalName("hasMapField_"), isPresent());

      assertThat(
          usedRootClassSubject.uniqueFieldWithOriginalName("isExtendedWithRequiredField_"),
          isPresent());
      assertThat(
          usedRootClassSubject.uniqueFieldWithOriginalName(
              "isRepeatedlyExtendedWithRequiredField_"),
          isPresent());

      ClassSubject hasRequiredFieldClassSubject = outputInspector.clazz(HAS_REQUIRED_FIELD);
      assertThat(hasRequiredFieldClassSubject, isPresent());
      assertThat(hasRequiredFieldClassSubject.uniqueFieldWithOriginalName("value_"), isPresent());
    }

    // Verify the absence of various fields in the output.
    {
      ClassSubject usedRootClassSubject = outputInspector.clazz(USED_ROOT);
      assertThat(usedRootClassSubject, isPresent());
      assertThat(
          usedRootClassSubject.uniqueFieldWithOriginalName("isExtendedWithOptional_"),
          not(isPresent()));
      assertThat(
          usedRootClassSubject.uniqueFieldWithOriginalName("isExtendedWithScalars_"),
          not(isPresent()));
    }
  }

  private void verifyUnusedExtensionsAreRemoved(
      CodeInspector inputInspector, CodeInspector outputInspector) {
    verifyUnusedExtensionsAreRemoved(
        inputInspector,
        outputInspector,
        "com.google.protobuf.proto2_registryGeneratedExtensionRegistryLite");
  }

  private void verifyUnusedExtensionsAreRemoved(
      CodeInspector inputInspector, CodeInspector outputInspector, String extensionRegistryName) {
    // Verify that the registry was split across multiple methods in the input.
    {
      ClassSubject generatedExtensionRegistryLoader = inputInspector.clazz(extensionRegistryName);
      assertThat(generatedExtensionRegistryLoader, isPresent());
      assertThat(
          generatedExtensionRegistryLoader.uniqueMethodWithOriginalName(
              "findLiteExtensionByNumber"),
          isPresent());
      assertThat(
          generatedExtensionRegistryLoader.uniqueMethodWithOriginalName(
              "findLiteExtensionByNumber1"),
          isPresent());
      assertThat(
          generatedExtensionRegistryLoader.uniqueMethodWithOriginalName(
              "findLiteExtensionByNumber2"),
          isPresent());
    }

    // Verify that the registry methods are still present in the output.
    //
    // We expect findLiteExtensionByNumber2() to be inlined into findLiteExtensionByNumber1() and
    // findLiteExtensionByNumber1() to be inlined into findLiteExtensionByNumber().
    {
      ClassSubject generatedExtensionRegistryLoader = outputInspector.clazz(extensionRegistryName);
      assertThat(generatedExtensionRegistryLoader, isPresent());
      assertThat(
          generatedExtensionRegistryLoader.uniqueMethodWithOriginalName(
              "findLiteExtensionByNumber"),
          isPresent());
      assertThat(
          generatedExtensionRegistryLoader.uniqueMethodWithOriginalName(
              "findLiteExtensionByNumber1"),
          isAbsentIf(enableMinification));
      assertThat(
          generatedExtensionRegistryLoader.uniqueMethodWithOriginalName(
              "findLiteExtensionByNumber2"),
          isAbsentIf(enableMinification));
    }

    // Verify that unused extensions have been removed with -allowaccessmodification.
    if (allowAccessModification) {
      List<String> unusedExtensionNames =
          ImmutableList.of(FLAGGED_OFF_EXTENSION, HAS_NO_USED_EXTENSIONS, EXT_B, EXT_C);
      for (String unusedExtensionName : unusedExtensionNames) {
        assertThat(inputInspector.clazz(unusedExtensionName), isPresent());
        assertThat(
            unusedExtensionName, outputInspector.clazz(unusedExtensionName), not(isPresent()));
      }
    }
  }

  private void verifyUnusedFieldsAreRemoved(
      CodeInspector inputInspector, CodeInspector outputInspector) {
    // Verify that various proto fields are present the input.
    {
      ClassSubject cfofClassSubject = inputInspector.clazz(CONTAINS_FLAGGED_OFF_FIELD);
      assertThat(cfofClassSubject, isPresent());
      assertThat(cfofClassSubject.uniqueFieldWithOriginalName("conditionallyUsed_"), isPresent());

      ClassSubject puClassSubject = inputInspector.clazz(PARTIALLY_USED);
      assertThat(puClassSubject, isPresent());
      assertEquals(7, puClassSubject.allInstanceFields().size());
      assertThat(puClassSubject.uniqueFieldWithOriginalName("bitField0_"), isPresent());
      assertThat(puClassSubject.uniqueFieldWithOriginalName("used_"), isPresent());
      assertThat(puClassSubject.uniqueFieldWithOriginalName("completelyUnused_"), isPresent());
      assertThat(puClassSubject.uniqueFieldWithOriginalName("unusedEnum_"), isPresent());
      assertThat(puClassSubject.uniqueFieldWithOriginalName("unusedRepeatedEnum_"), isPresent());
      assertThat(puClassSubject.uniqueFieldWithOriginalName("unusedMessage_"), isPresent());
      assertThat(puClassSubject.uniqueFieldWithOriginalName("unusedRepeatedMessage_"), isPresent());

      ClassSubject uvhClassSubject = inputInspector.clazz(USED_VIA_HAZZER);
      assertThat(uvhClassSubject, isPresent());
      assertThat(uvhClassSubject.uniqueFieldWithOriginalName("used_"), isPresent());
      assertThat(uvhClassSubject.uniqueFieldWithOriginalName("unused_"), isPresent());
    }

    // Verify that various proto fields have been removed in the output.
    {
      ClassSubject cfofClassSubject = outputInspector.clazz(CONTAINS_FLAGGED_OFF_FIELD);
      assertThat(cfofClassSubject, isPresent());
      assertThat(
          cfofClassSubject.uniqueFieldWithOriginalName("conditionallyUsed_"), not(isPresent()));

      ClassSubject puClassSubject = outputInspector.clazz(PARTIALLY_USED);
      assertThat(puClassSubject, isPresent());
      assertThat(puClassSubject.uniqueFieldWithOriginalName("bitField0_"), isPresent());
      assertThat(puClassSubject.uniqueFieldWithOriginalName("used_"), isPresent());
      assertThat(puClassSubject.uniqueFieldWithOriginalName("completelyUnused_"), not(isPresent()));
      assertThat(puClassSubject.uniqueFieldWithOriginalName("unusedEnum_"), not(isPresent()));
      assertThat(
          puClassSubject.uniqueFieldWithOriginalName("unusedRepeatedEnum_"), not(isPresent()));
      assertThat(puClassSubject.uniqueFieldWithOriginalName("unusedMessage_"), not(isPresent()));
      assertThat(
          puClassSubject.uniqueFieldWithOriginalName("unusedRepeatedMessage_"), not(isPresent()));

      ClassSubject uvhClassSubject = outputInspector.clazz(USED_VIA_HAZZER);
      assertThat(uvhClassSubject, isPresent());
      assertThat(uvhClassSubject.uniqueFieldWithOriginalName("used_"), isPresent());
      assertThat(uvhClassSubject.uniqueFieldWithOriginalName("unused_"), not(isPresent()));
    }
  }

  private void verifyUnusedHazzerBitFieldsAreRemoved(
      CodeInspector inputInspector, CodeInspector outputInspector) {
    // Verify that various proto fields are present the input.
    {
      ClassSubject classSubject = inputInspector.clazz(USES_ONLY_REPEATED_FIELDS);
      assertThat(classSubject, isPresent());
      assertThat(classSubject.uniqueFieldWithOriginalName("bitField0_"), isPresent());
      assertThat(classSubject.uniqueFieldWithOriginalName("myoneof_"), isPresent());
      assertThat(classSubject.uniqueFieldWithOriginalName("myoneofCase_"), isPresent());
    }

    // Verify that various proto fields have been removed in the output.
    {
      ClassSubject classSubject = outputInspector.clazz(USES_ONLY_REPEATED_FIELDS);
      assertThat(classSubject, isPresent());
      assertThat(classSubject.uniqueFieldWithOriginalName("bitField0_"), not(isPresent()));
      assertThat(classSubject.uniqueFieldWithOriginalName("myoneof_"), not(isPresent()));
      assertThat(classSubject.uniqueFieldWithOriginalName("myoneofCase_"), not(isPresent()));
    }
  }

  private void verifyUnusedTypesAreRemoved(
      CodeInspector inputInspector, CodeInspector outputInspector) {
    // Verify that various types are present the input.
    {
      ClassSubject enumClassSubject = inputInspector.clazz(PARTIALLY_USED + "$Enum");
      assertThat(enumClassSubject, isPresent());

      ClassSubject nestedClassSubject = inputInspector.clazz(PARTIALLY_USED + "$Nested");
      assertThat(nestedClassSubject, isPresent());
    }

    // Verify that various types have been removed in the output.
    {
      ClassSubject enumClassSubject = outputInspector.clazz(PARTIALLY_USED + "$Enum");
      assertThat(enumClassSubject, not(isPresent()));

      ClassSubject nestedClassSubject = outputInspector.clazz(PARTIALLY_USED + "$Nested");
      assertThat(nestedClassSubject, not(isPresent()));
    }
  }

  @Test
  public void testR8NoRewriting() throws Exception {
    protoRuntime.assumeIsNewerThanOrEqualToMinimumRequiredRuntime(protoTestSources);
    testForR8(parameters.getBackend())
        .apply(protoRuntime::addRuntime)
        .apply(protoRuntime::workaroundProtoMessageRemoval)
        .addProgramFiles(protoTestSources.getProgramFiles())
        .addKeepMainRule(MAIN)
        // Retain all protos.
        .addKeepRules(keepAllProtosRule())
        // Retain the signature of dynamicMethod() and newMessageInfo().
        .addKeepRules(keepDynamicMethodSignatureRule(), keepNewMessageInfoSignatureRule())
        .allowAccessModification(allowAccessModification)
        .allowDiagnosticMessages()
        .allowUnusedDontWarnPatterns()
        .allowUnusedProguardConfigurationRules()
        .enableProtoShrinking()
        .addDontObfuscateUnless(enableMinification)
        .setMinApi(parameters)
        .compile()
        .assertAllInfoMessagesMatch(
            containsString("Proguard configuration rule does not match anything"))
        .apply(this::inspectWarningMessages)
        .inspect(
            inspector ->
                assertRewrittenProtoSchemasMatch(protoTestSources.getInspector(), inspector));
  }

  @Test
  public void testR8TwoExtensionRegistries() throws Exception {
    protoRuntime.assumeIsNewerThanOrEqualToMinimumRequiredRuntime(protoTestSources);
    assumeTrue("Only proto2 has two extension registries", protoTestSources.isProto2());
    testForR8(parameters.getBackend())
        .apply(protoRuntime::addRuntime)
        .apply(protoRuntime::workaroundProtoMessageRemoval)
        .addProgramFiles(protoTestSources.getProgramFiles())
        .addKeepMainRule(MAIN)
        .addKeepRules(findLiteExtensionByNumberInDuplicateCalledRule())
        // TODO(b/173340579): This rule should not be needed to allow shrinking of
        //  PartiallyUsed$Enum.
        .addNoHorizontalClassMergingRule(PARTIALLY_USED + "$Enum$1")
        .allowAccessModification(allowAccessModification)
        .allowDiagnosticMessages()
        .allowUnusedDontWarnPatterns()
        .allowUnusedProguardConfigurationRules()
        .enableProtoShrinking()
        .addDontObfuscateUnless(enableMinification)
        .setMinApi(parameters)
        .compile()
        .assertAllInfoMessagesMatch(
            containsString("Proguard configuration rule does not match anything"))
        .apply(this::inspectWarningMessages)
        .inspect(
            outputInspector -> {
              CodeInspector inputInspector = protoTestSources.getInspector();
              verifyUnusedExtensionsAreRemoved(inputInspector, outputInspector);
              verifyUnusedExtensionsAreRemoved(
                  inputInspector,
                  outputInspector,
                  "com.google.protobuf.proto2_registryGeneratedExtensionRegistryLiteDuplicate");
            });
  }
}
