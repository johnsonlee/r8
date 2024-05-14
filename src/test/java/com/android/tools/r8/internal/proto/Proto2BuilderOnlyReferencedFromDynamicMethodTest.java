// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.internal.proto;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbstract;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

// TODO(b/339100248): Rename class to ProtoBuilderOnlyReferencedFromDynamicMethodTest.
// TODO(b/339100248): Avoid creating edition2023 messages in proto2 package.
//  Instead move proto2 and proto edition2023 messages to com.android.tools.r8.proto.
@RunWith(Parameterized.class)
public class Proto2BuilderOnlyReferencedFromDynamicMethodTest extends ProtoShrinkingTestBase {

  private static final String MAIN = "proto2.BuilderOnlyReferencedFromDynamicMethodTestClass";

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public ProtoRuntime protoRuntime;

  @Parameter(2)
  public ProtoTestSources protoTestSources;

  @Parameters(name = "{0}, {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
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
    testForR8(parameters.getBackend())
        .apply(protoRuntime::addRuntime)
        .apply(protoRuntime::workaroundProtoMessageRemoval)
        .addProgramFiles(protoTestSources.getProgramFiles())
        .addKeepMainRule(MAIN)
        .allowAccessModification()
        .allowDiagnosticMessages()
        .allowUnusedDontWarnPatterns()
        .allowUnusedProguardConfigurationRules()
        .enableProtoShrinking()
        .setMinApi(parameters)
        .compile()
        .assertAllInfoMessagesMatch(
            containsString("Proguard configuration rule does not match anything"))
        .apply(this::inspectWarningMessages)
        .inspect(this::inspect)
        .run(parameters.getRuntime(), MAIN)
        .apply(this::checkRunResult);
  }

  private void inspect(CodeInspector outputInspector) {
    verifyBuilderIsAbsent(outputInspector);
  }

  private void verifyBuilderIsAbsent(CodeInspector outputInspector) {
    ClassSubject generatedMessageLiteBuilder =
        outputInspector.clazz("com.google.protobuf.GeneratedMessageLite$Builder");
    assertThat(generatedMessageLiteBuilder, isPresent());
    assertThat(generatedMessageLiteBuilder, not(isAbstract()));
    assertThat(
        outputInspector.clazz("com.android.tools.r8.proto2.TestProto$Primitives$Builder"),
        isAbsent());
  }

  private void checkRunResult(SingleTestRunResult<?> runResult) {
    runResult.applyIf(
        protoTestSources.getCorrespondingRuntime() == protoRuntime,
        rr ->
            rr.assertSuccessWithOutputLines(
                "false", "0", "false", "", "false", "0", "false", "0", "false", ""),
        rr -> rr.assertFailureWithErrorThatThrows(ArrayIndexOutOfBoundsException.class));
  }
}
