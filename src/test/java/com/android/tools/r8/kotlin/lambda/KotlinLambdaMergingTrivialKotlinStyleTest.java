// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.lambda;

import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.KOTLINC_1_5_0;
import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.KOTLINC_1_6_0;
import static junit.framework.TestCase.assertEquals;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion;
import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.HorizontallyMergedClassesInspector;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KotlinLambdaMergingTrivialKotlinStyleTest extends KotlinTestBase {

  private final boolean allowAccessModification;
  private final TestParameters parameters;

  @Parameters(name = "{0}, {1}, allow access modification: {2}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters()
            // Exclude Kotlin 1.3, as that does not support trailing comma in multi line argument
            // lists, which the Kotlin formatter enforces.
            .withCompilersStartingFromIncluding(KotlinCompilerVersion.KOTLINC_1_4_20)
            .withAllLambdaGenerations()
            .withAllTargetVersions()
            .build(),
        BooleanUtils.values());
  }

  public KotlinLambdaMergingTrivialKotlinStyleTest(
      TestParameters parameters,
      KotlinTestParameters kotlinParameters,
      boolean allowAccessModification) {
    super(kotlinParameters);
    this.allowAccessModification = allowAccessModification;
    this.parameters = parameters;
  }

  @Test
  public void testJVM() throws Exception {
    assumeFalse(allowAccessModification);
    assumeTrue(parameters.isCfRuntime());
    assumeTrue(kotlinParameters.isFirst());
    testForJvm(parameters)
        .addProgramFiles(getProgramFiles())
        .run(parameters.getRuntime(), getMainClassName())
        .assertSuccessWithOutput(getExpectedOutput());
  }

  @Test
  public void testR8() throws Exception {
    // Get the Kotlin lambdas in the input.
    KotlinLambdasInInput lambdasInInput =
        KotlinLambdasInInput.create(getProgramFiles(), getTestName());
    assertEquals(0, lambdasInInput.getNumberOfJStyleLambdas());
    assertEquals(
        kotlinParameters.getLambdaGeneration().isInvokeDynamic() ? 0 : 28,
        lambdasInInput.getNumberOfKStyleLambdas());

    testForR8(parameters.getBackend())
        .addProgramFiles(getProgramFiles())
        .addKeepMainRule(getMainClassName())
        .addHorizontallyMergedClassesInspector(inspector -> inspect(inspector, lambdasInInput))
        .allowAccessModification(allowAccessModification)
        .setMinApi(parameters)
        .compile()
        .inspect(inspector -> inspect(inspector, lambdasInInput))
        .run(parameters.getRuntime(), getMainClassName())
        .assertSuccessWithOutput(getExpectedOutput());
  }

  private void inspect(
      HorizontallyMergedClassesInspector inspector, KotlinLambdasInInput lambdasInInput) {
    if (parameters.isCfRuntime()) {
      inspector.applyIf(
          kotlinParameters.getLambdaGeneration().isInvokeDynamic(),
          i ->
              i.assertIsCompleteMergeGroup(
                      Reference.classFromTypeName("kotlin.jvm.functions.Function0"),
                      Reference.classFromTypeName("kotlin.jvm.functions.Function1"),
                      Reference.classFromTypeName("kotlin.jvm.functions.Function2"),
                      Reference.classFromTypeName("kotlin.jvm.functions.Function3"),
                      Reference.classFromTypeName("kotlin.jvm.functions.Function22"))
                  .assertNoOtherClassesMerged());
    } else {
      ClassReference mainKt = Reference.classFromTypeName(getMainClassName());
      inspector
          .applyIf(
              kotlinParameters.getLambdaGeneration().isClass(),
              i -> {
                if (kotlinParameters.is(KOTLINC_1_5_0) || kotlinParameters.is(KOTLINC_1_6_0)) {
                  i.assertIsCompleteMergeGroup(
                      lambdasInInput.getKStyleLambdaReferenceFromTypeName(
                          getTestName(), "MainKt$testStateless$6"),
                      lambdasInInput.getKStyleLambdaReferenceFromTypeName(
                          getTestName(), "MainKt$testStateless$11"),
                      lambdasInInput.getKStyleLambdaReferenceFromTypeName(
                          getTestName(), "MainKt$testStateless$12"));
                } else {
                  i.assertIsCompleteMergeGroup(
                      lambdasInInput.getKStyleLambdaReferenceFromTypeName(
                          getTestName(), "MainKt$testStateless$11"),
                      lambdasInInput.getKStyleLambdaReferenceFromTypeName(
                          getTestName(), "MainKt$testStateless$12"));
                }
              },
              i ->
                  i.assertIsCompleteMergeGroup(
                      SyntheticItemsTestUtils.syntheticLambdaClass(mainKt, 0),
                      SyntheticItemsTestUtils.syntheticLambdaClass(mainKt, 1)))
          .assertNoOtherClassesMerged();
    }
  }

  private void inspect(CodeInspector inspector, KotlinLambdasInInput lambdasInInput) {
    List<ClassReference> lambdasInOutput = new ArrayList<>();
    for (ClassReference classReference : lambdasInInput.getKStyleLambdas()) {
      if (inspector.clazz(classReference).isPresent()) {
        lambdasInOutput.add(classReference);
      }
    }
    assertEquals(
        kotlinParameters.getLambdaGeneration().isInvokeDynamic()
            ? 0
            : allowAccessModification && parameters.isCfRuntime() ? 1 : 1,
        lambdasInOutput.size());
  }

  private String getExpectedOutput() {
    return StringUtils.lines(
        "first empty",
        "second empty",
        "first single",
        "second single",
        "third single",
        "caught: exception#14",
        "15",
        "16-17",
        "181920",
        "one-two-three",
        "one-two-...-twentythree",
        "46474849505152535455565758596061626364656667",
        "first empty",
        "second empty",
        "first single",
        "second single",
        "third single",
        "71",
        "72-73",
        "1",
        "5",
        "8",
        "20",
        "5",
        "",
        "kotlin.Unit",
        "10",
        "kotlin.Unit",
        "13",
        "kotlin.Unit",
        "14 -- 10",
        "kotlin.Unit");
  }

  private Path getJavaJarFile() {
    return getJavaJarFile(getTestName());
  }

  private String getMainClassName() {
    return getTestName() + ".MainKt";
  }

  private List<Path> getProgramFiles() {
    Path kotlinJarFile =
        getCompileMemoizer(getKotlinFilesInResource(getTestName()), getTestName())
            .configure(kotlinCompilerTool -> kotlinCompilerTool.includeRuntime().noReflect())
            .getForConfiguration(kotlinParameters);
    return ImmutableList.of(kotlinJarFile, getJavaJarFile(), kotlinc.getKotlinAnnotationJar());
  }

  private String getTestName() {
    return "lambdas_kstyle_trivial";
  }
}
