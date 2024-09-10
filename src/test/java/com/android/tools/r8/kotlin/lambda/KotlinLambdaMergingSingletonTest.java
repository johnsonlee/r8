// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.lambda;

import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.KOTLINC_1_9_21;
import static junit.framework.TestCase.assertEquals;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.utils.AndroidApiLevel;
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
public class KotlinLambdaMergingSingletonTest extends KotlinTestBase {

  private final boolean allowAccessModification;
  private final TestParameters parameters;

  @Parameters(name = "{0}, {1}, allow access modification: {2}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters().withAllCompilersLambdaGenerationsAndTargetVersions().build(),
        BooleanUtils.values());
  }

  public KotlinLambdaMergingSingletonTest(
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
    assertEquals(
        kotlinParameters.getLambdaGeneration().isInvokeDynamic()
            ? 8
            : 2,
        lambdasInInput.getNumberOfJStyleLambdas());
    assertEquals(
        kotlinParameters.getLambdaGeneration().isInvokeDynamic() ? 0 : 7,
        lambdasInInput.getNumberOfKStyleLambdas());

    testForR8(parameters.getBackend())
        .addProgramFiles(getProgramFiles())
        .addKeepMainRule(getMainClassName())
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
    // All J-style Kotlin lambdas should be merged into one class.
    if (kotlinParameters.getCompilerVersion().isLessThanOrEqualTo(KOTLINC_1_9_21)) {
      inspector.assertIsCompleteMergeGroup(lambdasInInput.getJStyleLambdas());
    } else {
      assertEquals(
          parameters.isCfRuntime() || parameters.getApiLevel() == AndroidApiLevel.B ? 3 : 4,
          inspector.getMergeGroups().size());
    }

    // The remaining lambdas are not merged.
    inspector.assertClassReferencesNotMerged(lambdasInInput.getKStyleLambdas());
  }

  private void inspect(CodeInspector inspector, KotlinLambdasInInput lambdasInInput) {
    List<ClassReference> lambdasInOutput = new ArrayList<>();
    for (ClassReference classReference : lambdasInInput.getAllLambdas()) {
      if (inspector.clazz(classReference).isPresent()) {
        lambdasInOutput.add(classReference);
      }
    }
    assertEquals(2, lambdasInOutput.size());
  }

  private String getExpectedOutput() {
    return StringUtils.lines(
        "(*000*001*002*003*004*005*006*007*008*009*)",
        "(*000*001*002*003*004*005*006*007*008*009*)",
        "(*010*011*)",
        "(*012*013*014*)",
        "(*015*016*)",
        "(*017*018*019*)");
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
    return "lambdas_singleton";
  }
}
