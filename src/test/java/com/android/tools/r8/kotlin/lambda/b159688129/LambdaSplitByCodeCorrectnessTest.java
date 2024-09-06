// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.lambda.b159688129;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentIf;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion;
import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LambdaSplitByCodeCorrectnessTest extends KotlinTestBase {

  private final TestParameters parameters;
  private final boolean splitGroup;

  @Parameters(name = "{0}, {1}, splitGroup: {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimesAndAllApiLevels().build(),
        getKotlinTestParameters().withAllCompilersLambdaGenerationsAndTargetVersions().build(),
        BooleanUtils.values());
  }

  public LambdaSplitByCodeCorrectnessTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters, boolean splitGroup) {
    super(kotlinParameters);
    this.parameters = parameters;
    this.splitGroup = splitGroup;
  }

  @Test
  public void testSplitLambdaGroups() throws Exception {
    String PKG_NAME = LambdaSplitByCodeCorrectnessTest.class.getPackage().getName();
    String folder = DescriptorUtils.getBinaryNameFromJavaType(PKG_NAME);
    CfRuntime cfRuntime =
        parameters.isCfRuntime() ? parameters.getRuntime().asCf() : TestRuntime.getCheckedInJdk9();
    Path ktClasses =
        kotlinc(cfRuntime, kotlinParameters)
            .addSourceFiles(getKotlinFileInTest(folder, "Simple"))
            .compile();
    testForR8(parameters.getBackend())
        .addProgramFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinAnnotationJar())
        .addProgramFiles(ktClasses)
        .setMinApi(parameters)
        .addKeepMainRule(PKG_NAME + ".SimpleKt")
        .applyIf(
            splitGroup,
            b ->
                b.addOptionsModification(
                    internalOptions ->
                        // Setting inliningInstructionAllowance = 1 will force each switch branch to
                        // contain an invoke instruction to a private method.
                        internalOptions.inlinerOptions().inliningInstructionAllowance = 1))
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector
                    .applyIf(
                        splitGroup && kotlinParameters.getLambdaGeneration().isClass(),
                        i ->
                            i.assertIsCompleteMergeGroup(
                                "com.android.tools.r8.kotlin.lambda.b159688129.SimpleKt$main$1",
                                "com.android.tools.r8.kotlin.lambda.b159688129.SimpleKt$main$2",
                                "com.android.tools.r8.kotlin.lambda.b159688129.SimpleKt$main$3",
                                "com.android.tools.r8.kotlin.lambda.b159688129.SimpleKt$main$4",
                                "com.android.tools.r8.kotlin.lambda.b159688129.SimpleKt$main$5",
                                "com.android.tools.r8.kotlin.lambda.b159688129.SimpleKt$main$6"))
                    .assertNoOtherClassesMerged())
        .compile()
        .inspectIf(
            kotlinParameters.getLambdaGeneration().isClass(),
            codeInspector -> {
              ClassSubject mergeTarget =
                  codeInspector.clazz(
                      "com.android.tools.r8.kotlin.lambda.b159688129.SimpleKt$main$1");
              assertThat(mergeTarget, isPresentIf(splitGroup));
              if (mergeTarget.isAbsent()) {
                return;
              }

              boolean isKotlinOld =
                  kotlinc.isOneOf(
                      KotlinCompilerVersion.KOTLINC_1_3_72, KotlinCompilerVersion.KOTLINC_1_4_20);
              MethodSubject virtualMethodSubject =
                  mergeTarget.uniqueMethodThatMatches(
                      method ->
                          method.isVirtual()
                              && !method.isSynthetic()
                              && method
                                  .getOriginalMethodName()
                                  .equals(isKotlinOld ? "invoke$1" : "invoke"));
              assertThat(virtualMethodSubject, isPresent());

              int found = 0;
              for (FoundMethodSubject directMethodSubject :
                  mergeTarget.allMethods(x -> x.isPrivate() && !x.isSynthetic())) {
                assertThat(virtualMethodSubject, invokesMethod(directMethodSubject));
                found++;
              }
              assertEquals(splitGroup ? 6 : 0, found);
            })
        .run(parameters.getRuntime(), PKG_NAME + ".SimpleKt")
        .assertSuccessWithOutputLines("Hello1", "Hello2", "Hello3", "Hello4", "Hello5", "Hello6");
  }
}
