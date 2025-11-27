// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.kotlin;

import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.KOTLINC_1_3_72;
import static com.android.tools.r8.KotlinTestBase.getCompileMemoizer;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.KotlinCompileMemoizer;
import com.android.tools.r8.KotlinCompilerTool.KotlinCompiler;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.DesugaredLibraryTestBuilder;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.kotlin.KotlinMetadataWriter;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.ConsumerUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Consumer;
import kotlin.metadata.jvm.KotlinClassMetadata;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KotlinMetadataTest extends DesugaredLibraryTestBase {

  private static final String PKG = KotlinMetadataTest.class.getPackage().getName();
  private static final String EXPECTED_OUTPUT = "Wuhuu, my special day is: 1997-8-29-2-14";

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public KotlinTestParameters kotlinParameters;

  @Parameter(2)
  public CompilationSpecification compilationSpecification;

  @Parameter(3)
  public LibraryDesugaringSpecification libraryDesugaringSpecification;

  @Parameters(name = "{0}, kotlin: {1}, spec: {2}, {3}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters().withAllCompilersAndLambdaGenerations().build(),
        DEFAULT_SPECIFICATIONS,
        ImmutableList.of(LibraryDesugaringSpecification.JDK11));
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    KotlinCompiler kotlinc = kotlinParameters.getCompiler();
    testForRuntime(parameters)
        .addProgramFiles(compiledJars.getForConfiguration(kotlinParameters))
        .addProgramFiles(kotlinc.getKotlinStdlibJar())
        .addProgramFiles(kotlinc.getKotlinReflectJar())
        .run(parameters.getRuntime(), PKG + ".MainKt")
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  @Test
  public void testDesugaredLibrary() throws Exception {
    parameters.assumeDexRuntime();
    runTest(ConsumerUtils.emptyConsumer());
  }

  @Test
  public void testR8PartialIncludeKotlinStdlib() throws Exception {
    parameters.assumeDexRuntime();
    assumeTrue(compilationSpecification.isProgramShrinkWithPartial());
    runTest(
        desugaredLibraryTestBuilder ->
            desugaredLibraryTestBuilder.applyIfR8PartialTestBuilder(
                r8PartialTestBuilder ->
                    r8PartialTestBuilder
                        .allowUnusedProguardConfigurationRules()
                        .clearR8PartialConfiguration()
                        .setR8PartialConfiguration(
                            partialConfigurationBuilder ->
                                partialConfigurationBuilder.addJavaTypeIncludePattern(
                                    "kotlin.**"))));
  }

  private void runTest(Consumer<DesugaredLibraryTestBuilder<?>> configuration) throws Exception {
    KotlinCompiler kotlinc = kotlinParameters.getCompiler();
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addProgramFiles(compiledJars.getForConfiguration(kotlinParameters))
        .addProgramFiles(kotlinc.getKotlinStdlibJar())
        .addProgramFiles(kotlinc.getKotlinReflectJar())
        .addProgramFiles(kotlinc.getKotlinAnnotationJar())
        .enableServiceLoader()
        .addKeepMainRule(PKG + ".MainKt")
        .addKeepRules(
            "-keep,allowobfuscation class " + PKG + ".Skynet",
            // TODO(b/409735960): We don't seem to correctly rewrite the metadata when adding
            //  `,allowobfuscation` to this rule.
            "-keepclassmembers class " + PKG + ".Skynet { * specialDay; }")
        .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
        .allowDiagnosticMessages()
        // The Kotlin 1.3.72 reflect jar does not embed a keep rule for kotlin.Metadata.
        .applyIf(
            kotlinParameters.getCompiler().is(KOTLINC_1_3_72),
            b -> b.addKeepRules("-keep class kotlin.Metadata { *; }"),
            DesugaredLibraryTestBuilder::allowUnusedDontWarnPatterns)
        .applyIf(
            kotlinParameters.getCompiler().isNot(KOTLINC_1_3_72)
                && compilationSpecification.isNotProgramShrinkWithPartial(),
            DesugaredLibraryTestBuilder::allowUnusedProguardConfigurationRules)
        .apply(configuration)
        .compile()
        .inspect(
            i -> {
              if (libraryDesugaringSpecification.hasTimeDesugaring(parameters)) {
                inspectRewrittenMetadata(i);
              }
            })
        .run(parameters.getRuntime(), PKG + ".MainKt")
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  private static KotlinCompileMemoizer compiledJars =
      getCompileMemoizer(
          Paths.get(
              ToolHelper.TESTS_DIR,
              "java",
              DescriptorUtils.getBinaryNameFromJavaType(PKG),
              "Main" + FileUtils.KT_EXTENSION));

  private void inspectRewrittenMetadata(CodeInspector inspector) {
    ClassSubject clazz =
        inspector.clazz("com.android.tools.r8.desugar.desugaredlibrary.kotlin.Skynet");
    assertThat(clazz, isPresent());
    KotlinClassMetadata kotlinClassMetadata = clazz.getKotlinClassMetadata();
    assertNotNull(kotlinClassMetadata);
    String metadata = KotlinMetadataWriter.kotlinMetadataToString("", kotlinClassMetadata);
    assertThat(metadata, containsString("specialDay:Lj$/time/LocalDateTime;"));
    assertThat(metadata, containsString("Class(name=j$/time/LocalDateTime)"));
    assertThat(metadata, not(containsString("java.time.LocalDateTime")));
  }
}
