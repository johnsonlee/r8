// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.lambda;

import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.KOTLINC_1_9_21;
import static com.android.tools.r8.shaking.ProguardKeepAttributes.ENCLOSING_METHOD;
import static com.android.tools.r8.shaking.ProguardKeepAttributes.INNER_CLASSES;
import static com.android.tools.r8.shaking.ProguardKeepAttributes.SIGNATURE;
import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

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
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KotlinLambdaMergingKeepAttributesKotlinStyleTest extends KotlinTestBase {

  private final boolean allowAccessModification;
  private final List<String> attributes;
  private final TestParameters parameters;

  @Parameters(name = "{0}, {1}, allow access modification: {2}, attributes: {3}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters().withAllCompilersLambdaGenerationsAndTargetVersions().build(),
        BooleanUtils.values(),
        ImmutableList.of(
            Collections.emptyList(),
            ImmutableList.of(ENCLOSING_METHOD, INNER_CLASSES),
            ImmutableList.of(ENCLOSING_METHOD, INNER_CLASSES, SIGNATURE)));
  }

  public KotlinLambdaMergingKeepAttributesKotlinStyleTest(
      TestParameters parameters,
      KotlinTestParameters kotlinParameters,
      boolean allowAccessModification,
      List<String> attributes) {
    super(kotlinParameters);
    this.allowAccessModification = allowAccessModification;
    this.attributes = attributes;
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
        kotlinParameters.getLambdaGeneration().isInvokeDynamic() ? 0 : 24,
        lambdasInInput.getNumberOfKStyleLambdas());

    testForR8(parameters.getBackend())
        .addProgramFiles(getProgramFiles())
        .addKeepMainRule(getMainClassName())
        .applyIf(!attributes.isEmpty(), builder -> builder.addKeepAttributes(attributes))
        .addHorizontallyMergedClassesInspector(inspector -> inspect(inspector, lambdasInInput))
        .allowAccessModification(allowAccessModification)
        .noClassInlining()
        .setMinApi(parameters)
        .compile()
        .inspect(inspector -> inspect(inspector, lambdasInInput))
        .run(parameters.getRuntime(), getMainClassName())
        .assertSuccessWithOutput(getExpectedOutput());
  }

  private void inspect(
      HorizontallyMergedClassesInspector inspector, KotlinLambdasInInput lambdasInInput) {
    if (parameters.isCfRuntime()) {
      if (kotlinParameters.getLambdaGeneration().isClass()) {
        inspector
            .assertIsCompleteMergeGroup(
                lambdasInInput.getKStyleLambdaReferenceFromTypeName(
                    getTestName(), "MainKt$testFirst$1"),
                lambdasInInput.getKStyleLambdaReferenceFromTypeName(
                    getTestName(), "MainKt$testFirst$2"),
                lambdasInInput.getKStyleLambdaReferenceFromTypeName(
                    getTestName(), "MainKt$testFirst$3"),
                lambdasInInput.getKStyleLambdaReferenceFromTypeName(
                    getTestName(), "MainKt$testFirst$4"),
                lambdasInInput.getKStyleLambdaReferenceFromTypeName(
                    getTestName(), "MainKt$testFirst$5"),
                lambdasInInput.getKStyleLambdaReferenceFromTypeName(
                    getTestName(), "MainKt$testFirst$6"),
                lambdasInInput.getKStyleLambdaReferenceFromTypeName(
                    getTestName(), "MainKt$testFirst$7"),
                lambdasInInput.getKStyleLambdaReferenceFromTypeName(
                    getTestName(), "MainKt$testFirst$8"),
                lambdasInInput.getKStyleLambdaReferenceFromTypeName(
                    getTestName(), "MainKt$testFirst$9"),
                lambdasInInput.getKStyleLambdaReferenceFromTypeName(
                    getTestName(), "MainKt$testSecond$1"),
                lambdasInInput.getKStyleLambdaReferenceFromTypeName(
                    getTestName(), "MainKt$testSecond$2"),
                lambdasInInput.getKStyleLambdaReferenceFromTypeName(
                    getTestName(), "MainKt$testSecond$3"),
                lambdasInInput.getKStyleLambdaReferenceFromTypeName(
                    getTestName(), "MainKt$testSecond$4"),
                lambdasInInput.getKStyleLambdaReferenceFromTypeName(
                    getTestName(), "MainKt$testSecond$5"),
                lambdasInInput.getKStyleLambdaReferenceFromTypeName(
                    getTestName(), "MainKt$testSecond$6"),
                lambdasInInput.getKStyleLambdaReferenceFromTypeName(
                    getTestName(), "MainKt$testSecond$7"),
                lambdasInInput.getKStyleLambdaReferenceFromTypeName(
                    getTestName(), "MainKt$testSecond$8"),
                lambdasInInput.getKStyleLambdaReferenceFromTypeName(
                    getTestName(), "MainKt$testSecond$9"),
                lambdasInInput.getKStyleLambdaReferenceFromTypeName(
                    getTestName(), "MainKt$testThird$1"),
                lambdasInInput.getKStyleLambdaReferenceFromTypeName(
                    getTestName(), "MainKt$testThird$2"))
            .assertNoOtherClassesMerged();
      } else {
        inspector.assertNoClassesMerged();
      }
    } else {
      if (kotlinParameters.getLambdaGeneration().isClass()) {
        inspector
            .assertIsCompleteMergeGroup(
                lambdasInInput.getKStyleLambdaReferenceFromTypeName(getTestName(), "MainKt$main$1"),
                lambdasInInput.getKStyleLambdaReferenceFromTypeName(getTestName(), "MainKt$main$2"),
                lambdasInInput.getKStyleLambdaReferenceFromTypeName(getTestName(), "MainKt$main$3"),
                lambdasInInput.getKStyleLambdaReferenceFromTypeName(getTestName(), "MainKt$main$4"),
                lambdasInInput.getKStyleLambdaReferenceFromTypeName(
                    getTestName(), "MainKt$testFirst$1"),
                lambdasInInput.getKStyleLambdaReferenceFromTypeName(
                    getTestName(), "MainKt$testFirst$2"),
                lambdasInInput.getKStyleLambdaReferenceFromTypeName(
                    getTestName(), "MainKt$testFirst$3"),
                lambdasInInput.getKStyleLambdaReferenceFromTypeName(
                    getTestName(), "MainKt$testFirst$4"),
                lambdasInInput.getKStyleLambdaReferenceFromTypeName(
                    getTestName(), "MainKt$testFirst$5"),
                lambdasInInput.getKStyleLambdaReferenceFromTypeName(
                    getTestName(), "MainKt$testFirst$6"),
                lambdasInInput.getKStyleLambdaReferenceFromTypeName(
                    getTestName(), "MainKt$testFirst$7"),
                lambdasInInput.getKStyleLambdaReferenceFromTypeName(
                    getTestName(), "MainKt$testFirst$8"),
                lambdasInInput.getKStyleLambdaReferenceFromTypeName(
                    getTestName(), "MainKt$testFirst$9"),
                lambdasInInput.getKStyleLambdaReferenceFromTypeName(
                    getTestName(), "MainKt$testSecond$1"),
                lambdasInInput.getKStyleLambdaReferenceFromTypeName(
                    getTestName(), "MainKt$testSecond$2"),
                lambdasInInput.getKStyleLambdaReferenceFromTypeName(
                    getTestName(), "MainKt$testSecond$3"),
                lambdasInInput.getKStyleLambdaReferenceFromTypeName(
                    getTestName(), "MainKt$testSecond$4"),
                lambdasInInput.getKStyleLambdaReferenceFromTypeName(
                    getTestName(), "MainKt$testSecond$5"),
                lambdasInInput.getKStyleLambdaReferenceFromTypeName(
                    getTestName(), "MainKt$testSecond$6"),
                lambdasInInput.getKStyleLambdaReferenceFromTypeName(
                    getTestName(), "MainKt$testSecond$7"),
                lambdasInInput.getKStyleLambdaReferenceFromTypeName(
                    getTestName(), "MainKt$testSecond$8"),
                lambdasInInput.getKStyleLambdaReferenceFromTypeName(
                    getTestName(), "MainKt$testSecond$9"),
                lambdasInInput.getKStyleLambdaReferenceFromTypeName(
                    getTestName(), "MainKt$testThird$1"),
                lambdasInInput.getKStyleLambdaReferenceFromTypeName(
                    getTestName(), "MainKt$testThird$2"))
            .assertNoOtherClassesMerged();
      } else {
        ClassReference mainKt = Reference.classFromTypeName(getMainClassName());
        List<ClassReference> mergeGroup =
            ImmutableList.of(
                SyntheticItemsTestUtils.syntheticLambdaClass(mainKt, 9),
                SyntheticItemsTestUtils.syntheticLambdaClass(mainKt, 10),
                SyntheticItemsTestUtils.syntheticLambdaClass(mainKt, 11),
                SyntheticItemsTestUtils.syntheticLambdaClass(mainKt, 12),
                SyntheticItemsTestUtils.syntheticLambdaClass(mainKt, 13),
                SyntheticItemsTestUtils.syntheticLambdaClass(mainKt, 14),
                SyntheticItemsTestUtils.syntheticLambdaClass(mainKt, 19),
                SyntheticItemsTestUtils.syntheticLambdaClass(mainKt, 18),
                SyntheticItemsTestUtils.syntheticLambdaClass(mainKt, 20),
                SyntheticItemsTestUtils.syntheticLambdaClass(mainKt, 21),
                SyntheticItemsTestUtils.syntheticLambdaClass(mainKt, 22),
                SyntheticItemsTestUtils.syntheticLambdaClass(mainKt, 23));
        List<ClassReference> otherMergeGroup =
            ImmutableList.of(
                SyntheticItemsTestUtils.syntheticLambdaClass(mainKt, 0),
                SyntheticItemsTestUtils.syntheticLambdaClass(mainKt, 1),
                SyntheticItemsTestUtils.syntheticLambdaClass(mainKt, 2),
                SyntheticItemsTestUtils.syntheticLambdaClass(mainKt, 3),
                SyntheticItemsTestUtils.syntheticLambdaClass(mainKt, 4),
                SyntheticItemsTestUtils.syntheticLambdaClass(mainKt, 5),
                SyntheticItemsTestUtils.syntheticLambdaClass(mainKt, 6),
                SyntheticItemsTestUtils.syntheticLambdaClass(mainKt, 7),
                SyntheticItemsTestUtils.syntheticLambdaClass(mainKt, 8),
                SyntheticItemsTestUtils.syntheticLambdaClass(mainKt, 15),
                SyntheticItemsTestUtils.syntheticLambdaClass(mainKt, 16),
                SyntheticItemsTestUtils.syntheticLambdaClass(mainKt, 17));
        inspector
            .applyIf(
                kotlinc.getCompilerVersion().isGreaterThanOrEqualTo(KOTLINC_1_9_21)
                    && parameters.isDexRuntime()
                    && lambdaGeneration.isInvokeDynamic(),
                i ->
                    i.assertIsCompleteMergeGroup(
                        ImmutableList.<ClassReference>builder()
                            .addAll(mergeGroup)
                            .addAll(otherMergeGroup)
                            .build()),
                i ->
                    i.assertIsCompleteMergeGroup(mergeGroup)
                        .assertIsCompleteMergeGroup(otherMergeGroup))
            .assertNoOtherClassesMerged();
      }
    }
  }

  private void inspect(CodeInspector inspector, KotlinLambdasInInput lambdasInInput) {
    List<ClassReference> lambdasInOutput = new ArrayList<>();
    for (ClassReference classReference : lambdasInInput.getAllLambdas()) {
      if (inspector.clazz(classReference).isPresent()) {
        lambdasInOutput.add(classReference);
      }
    }
    assertEquals(
        kotlinParameters.getLambdaGeneration().isInvokeDynamic() ? 0 : 1, lambdasInOutput.size());
  }

  private String getExpectedOutput() {
    return StringUtils.lines(
        "Alpha(id=11)",
        "Beta(id=12)",
        "Gamma(payload={any}, id=13)",
        "Alpha(id=14)",
        "First-1-Beta(id=15)",
        "First-2-Beta(id=16)",
        "First-3-Beta(id=17)",
        "First-A-Gamma(payload=18, id=19)-11",
        "First-B-Gamma(payload=20, id=21)-11",
        "First-C-Gamma(payload=22, id=23)-11",
        "First-D-Gamma(payload=24, id=25)-11",
        "First-E-Gamma(payload=26, id=27)-11",
        "First-F-Gamma(payload=28, id=29)-11",
        "Second-1-Beta(id=30)",
        "Second-2-Beta(id=31)",
        "Second-3-Beta(id=32)",
        "Second-A-Gamma(payload=33, id=34)-22",
        "Second-B-Gamma(payload=35, id=36)-22",
        "Second-C-Gamma(payload=37, id=38)-22",
        "Second-D-Gamma(payload=39, id=40)-22",
        "Second-E-Gamma(payload=41, id=42)-22",
        "Second-F-Gamma(payload=43, id=44)-22",
        "4321 45 46 47",
        "1234 Alpha(id=48) Beta(id=49) Gamma(payload=50, id=51)");
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
    return "lambdas_kstyle_generics";
  }
}
