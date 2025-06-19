// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.lambda.b148525512;

import com.android.tools.r8.DexIndexedConsumer.ArchiveConsumer;
import com.android.tools.r8.KotlinCompileMemoizer;
import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.ArchiveResourceProvider;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.HorizontallyMergedClassesInspector;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class B148525512 extends KotlinTestBase {

  private static final Package pkg = B148525512.class.getPackage();
  private static final String kotlinTestClassesPackage = pkg.getName();
  private static final String baseKtClassName = kotlinTestClassesPackage + ".BaseKt";
  private static final String featureKtClassName = kotlinTestClassesPackage + ".FeatureKt";
  private static final String baseClassName = kotlinTestClassesPackage + ".Base";

  private static Path featureApiPath;
  private static final KotlinCompileMemoizer kotlinBaseClasses =
      getCompileMemoizer(getKotlinFileInTestPackage(pkg, "base"))
          .configure(
              kotlinCompilerTool -> kotlinCompilerTool.addClasspathFiles(getFeatureApiPath()));
  private static final KotlinCompileMemoizer kotlinFeatureClasses =
      getCompileMemoizer(getKotlinFileInTestPackage(pkg, "feature"))
          .configure(
              kotlinCompilerTool -> {
                // Compile the feature Kotlin code with the base classes on classpath.
                kotlinCompilerTool.addClasspathFiles(
                    kotlinBaseClasses.getForConfiguration(
                        kotlinCompilerTool.getCompiler(),
                        kotlinCompilerTool.getTargetVersion(),
                        kotlinCompilerTool.getLambdaGeneration()));
              });
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        getKotlinTestParameters().withAllCompilersAndLambdaGenerations().build());
  }

  public B148525512(TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  private static Path getFeatureApiPath() {
    if (featureApiPath == null) {
      try {
        featureApiPath = writeClassesToJar(FeatureAPI.class);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    return featureApiPath;
  }

  @Test
  public void test() throws Exception {
    Path featureCode = temp.newFile("feature.zip").toPath();
    CodeInspector inputInspector =
        new CodeInspector(kotlinBaseClasses.getForConfiguration(kotlinParameters));
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addProgramFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinAnnotationJar())
            .addProgramFiles(kotlinBaseClasses.getForConfiguration(kotlinParameters))
            .addProgramClasses(FeatureAPI.class)
            .addKeepMainRule(baseKtClassName)
            .addKeepClassAndMembersRules(baseClassName)
            .addKeepClassAndMembersRules(featureKtClassName)
            .addKeepClassAndMembersRules(FeatureAPI.class)
            .addKeepRules(
                "-reprocessmethod class * {",
                "  void main(java.lang.String[]);",
                "  void feature(int);",
                "}")
            .addHorizontallyMergedClassesInspector(
                HorizontallyMergedClassesInspector::assertNoClassesMerged)
            .enableProguardTestOptions()
            .setMinApi(parameters)
            .addFeatureSplit(
                builder ->
                    builder
                        .addProgramResourceProvider(
                            ArchiveResourceProvider.fromArchive(
                                kotlinFeatureClasses.getForConfiguration(kotlinParameters), true))
                        .setProgramConsumer(new ArchiveConsumer(featureCode, false))
                        .build())
            .compile()
            .inspect(
                inspector -> {
                  if (kotlinParameters.getLambdaGeneration().isClass()) {
                    assertRemovedFromOutput(
                        "com.android.tools.r8.kotlin.lambda.b148525512.BaseKt$main$1",
                        inputInspector,
                        inspector);
                    assertRemovedFromOutput(
                        "com.android.tools.r8.kotlin.lambda.b148525512.BaseKt$main$2",
                        inputInspector,
                        inspector);
                    assertRemovedFromOutput(
                        "com.android.tools.r8.kotlin.lambda.b148525512.FeatureKt$feature$1",
                        inputInspector,
                        inspector);
                    assertRemovedFromOutput(
                        "com.android.tools.r8.kotlin.lambda.b148525512.FeatureKt$feature$2",
                        inputInspector,
                        inspector);
                  } else {
                    ClassReference baseKt =
                        Reference.classFromTypeName(
                            "com.android.tools.r8.kotlin.lambda.b148525512.BaseKt");
                    ClassReference featureKt =
                        Reference.classFromTypeName(
                            "com.android.tools.r8.kotlin.lambda.b148525512.FeatureKt");
                    assertRemovedFromOutput(
                        SyntheticItemsTestUtils.syntheticLambdaClass(baseKt, 0),
                        inputInspector,
                        inspector);
                    assertRemovedFromOutput(
                        SyntheticItemsTestUtils.syntheticLambdaClass(baseKt, 1),
                        inputInspector,
                        inspector);
                    assertRemovedFromOutput(
                        SyntheticItemsTestUtils.syntheticLambdaClass(featureKt, 0),
                        inputInspector,
                        inspector);
                    assertRemovedFromOutput(
                        SyntheticItemsTestUtils.syntheticLambdaClass(featureKt, 1),
                        inputInspector,
                        inspector);
                  }
                });

    // Run the code without the feature code.
    compileResult
        .run(parameters.getRuntime(), baseKtClassName)
        .assertSuccessWithOutputLines("1", "2");

    // Run the code with the feature code present.
    compileResult
        .addRunClasspathFiles(featureCode)
        .run(parameters.getRuntime(), baseKtClassName)
        .assertSuccessWithOutputLines("1", "2", "3", "4");
  }

  private void assertRemovedFromOutput(
      String clazz, CodeInspector inputInspector, CodeInspector outputInspector) {
    assertRemovedFromOutput(Reference.classFromTypeName(clazz), inputInspector, outputInspector);
  }

  private void assertRemovedFromOutput(
      ClassReference clazz, CodeInspector inputInspector, CodeInspector outputInspector) {}
}
