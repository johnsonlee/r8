// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial.kotlin;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;

import com.android.tools.r8.KotlinCompilerTool.KotlinCompiler;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.kotlin.KotlinMetadataWriter;
import com.android.tools.r8.kotlin.metadata.KotlinMetadataTestBase;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Paths;
import java.util.List;
import kotlin.metadata.jvm.KotlinClassMetadata;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PartialCompilationKotlinMetadataTest extends KotlinMetadataTestBase {

  private static final String PKG =
      PartialCompilationKotlinMetadataTest.class.getPackage().getName();
  private static final String GREETING_NAME = PKG + ".Greeting";
  private static final String MAIN_NAME = PKG + ".MainKt";
  private static final String MESSAGE1_NAME = PKG + ".Message1";
  private static final String MESSAGE2_NAME = PKG + ".Message2";

  private final TestParameters parameters;
  private final boolean includeKotlin;

  @Parameters(name = "{0}, kotlin: {1}, include kotlin.Metadata: {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters().withAllCompilersLambdaGenerationsAndTargetVersions().build(),
        BooleanUtils.values());
  }

  public PartialCompilationKotlinMetadataTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters, boolean includeKotlin) {
    super(kotlinParameters);
    this.parameters = parameters;
    this.includeKotlin = includeKotlin;
  }

  @Test
  public void test() throws Exception {
    parameters.assumeCanUseR8Partial();
    KotlinCompiler kotlinc = kotlinParameters.getCompiler();
    testForR8Partial(parameters.getBackend())
        .addProgramFiles(compiledJars.getForConfiguration(kotlinParameters))
        .addProgramFiles(kotlinc.getKotlinStdlibJar())
        .addProgramFiles(kotlinc.getKotlinAnnotationJar())
        .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
        .addKeepRuntimeVisibleAnnotations()
        .applyIf(includeKotlin, b -> b.addKeepRules("-keep class kotlin.Metadata { *; }"))
        .setMinApi(parameters)
        .setR8PartialConfiguration(
            partialConfigurationBuilder -> {
              partialConfigurationBuilder.addJavaTypeIncludePattern(MESSAGE2_NAME);
              if (includeKotlin) {
                partialConfigurationBuilder.addJavaTypeIncludePattern("kotlin.**");
              }
            })
        .allowDiagnosticMessages()
        .allowUnusedDontWarnPatterns()
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), MAIN_NAME)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject message1Class = inspector.clazz(MESSAGE1_NAME);
    assertThat(message1Class, isPresent());

    ClassSubject message2Class = inspector.clazz(MESSAGE2_NAME);
    assertThat(message2Class, isPresent());

    ClassSubject greetingClass = inspector.clazz(GREETING_NAME);
    assertThat(greetingClass, isPresent());
    KotlinClassMetadata kotlinClassMetadata = greetingClass.getKotlinClassMetadata();
    assertNotNull(kotlinClassMetadata);
    String metadata = KotlinMetadataWriter.kotlinMetadataToString("", kotlinClassMetadata);
    assertThat(metadata, containsString(greetingClass.getFinalBinaryName()));
    assertThat(metadata, containsString(message1Class.getFinalBinaryName()));
    assertThat(metadata, containsString(message2Class.getFinalBinaryName()));
  }

  private static final KotlinCompileMemoizer compiledJars =
      getCompileMemoizer(
          Paths.get(
              ToolHelper.TESTS_DIR,
              "java",
              DescriptorUtils.getBinaryNameFromJavaType(PKG),
              "Main" + FileUtils.KT_EXTENSION));
}
