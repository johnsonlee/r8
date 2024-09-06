// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.internal.proto;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

@RunWith(Parameterized.class)
public class Proto3ShrinkingTest extends ProtoShrinkingTestBase {

  private static final String PARTIALLY_USED =
      "com.android.tools.r8.proto3.Shrinking$PartiallyUsed";

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

  @Parameterized.Parameters(
      name = "{2}, {3}, {4}, allow access modification: {0}, enable minification: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        BooleanUtils.values(),
        getTestParameters().withDefaultDexRuntime().withAllApiLevels().build(),
        ProtoRuntime.values(),
        ImmutableList.of(ProtoTestSources.PROTO3));
  }

  @Test
  public void testR8() throws Exception {
    protoRuntime.assumeIsNewerThanOrEqualToMinimumRequiredRuntime(protoTestSources);
    testForR8(parameters.getBackend())
        .apply(protoRuntime::addRuntime)
        .apply(protoRuntime::workaroundProtoMessageRemoval)
        .addProgramFiles(protoTestSources.getProgramFiles())
        .addKeepMainRule("proto3.TestClass")
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
        .inspect(
            outputInspector -> {
              CodeInspector inputInspector = protoTestSources.getInspector();
              verifyUnusedFieldsAreRemoved(inputInspector, outputInspector);
            })
        .run(parameters.getRuntime(), "proto3.TestClass")
        .assertSuccessWithOutputLines("--- partiallyUsed_proto3 ---", "42");
  }

  private void verifyUnusedFieldsAreRemoved(
      CodeInspector inputInspector, CodeInspector outputInspector) {
    // Verify that various proto fields are present the input.
    {
      ClassSubject puClassSubject = inputInspector.clazz(PARTIALLY_USED);
      assertThat(puClassSubject, isPresent());
      assertEquals(2, puClassSubject.allInstanceFields().size());
      assertThat(puClassSubject.uniqueFieldWithOriginalName("used_"), isPresent());
      assertThat(puClassSubject.uniqueFieldWithOriginalName("completelyUnused_"), isPresent());
    }

    // Verify that various proto fields have been removed in the output.
    {
      ClassSubject puClassSubject = outputInspector.clazz(PARTIALLY_USED);
      assertThat(puClassSubject, isPresent());
      assertThat(puClassSubject.uniqueFieldWithOriginalName("used_"), isPresent());
      assertThat(puClassSubject.uniqueFieldWithOriginalName("completelyUnused_"), not(isPresent()));
    }
  }

  @Test
  public void testR8NoRewriting() throws Exception {
    protoRuntime.assumeIsNewerThanOrEqualToMinimumRequiredRuntime(protoTestSources);
    testForR8(parameters.getBackend())
        .apply(protoRuntime::addRuntime)
        .apply(protoRuntime::workaroundProtoMessageRemoval)
        .addProgramFiles(protoTestSources.getProgramFiles())
        .addKeepMainRule("proto3.TestClass")
        // Retain all protos.
        .addKeepRules(keepAllProtosRule())
        // Retain the signature of dynamicMethod() and newMessageInfo().
        .addKeepRules(keepDynamicMethodSignatureRule(), keepNewMessageInfoSignatureRule())
        .allowAccessModification(allowAccessModification)
        .allowDiagnosticInfoMessages()
        .allowUnusedDontWarnPatterns()
        .allowUnusedProguardConfigurationRules()
        .enableProtoShrinking()
        .addDontObfuscateUnless(enableMinification)
        .setMinApi(parameters)
        .compile()
        .assertAllInfoMessagesMatch(
            containsString("Proguard configuration rule does not match anything"))
        .inspect(
            inspector ->
                assertRewrittenProtoSchemasMatch(protoTestSources.getInspector(), inspector));
  }
}
