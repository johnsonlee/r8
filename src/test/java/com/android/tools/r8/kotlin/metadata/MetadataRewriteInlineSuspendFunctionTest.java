// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata;

import com.android.tools.r8.KotlinCompileMemoizer;
import com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewriteInlineSuspendFunctionTest extends KotlinMetadataTestBase {

  private final String EXPECTED = StringUtils.lines("foo");
  private static final String PKG_LIB = PKG + ".inline_suspend_lib";
  private static final String PKG_APP = PKG + ".inline_suspend_app";

  private final boolean keepOnlyPublic;
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{1}, {2}, keepOnlyPublic: {0}")
  public static Collection<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        getTestParameters().withCfRuntimes().build(),
        getKotlinTestParameters()
            .withOldCompilers()
            .withCompilersStartingFromIncluding(KotlinCompilerVersion.KOTLINC_1_6_0)
            .withAllLambdaGenerations()
            .withAllTargetVersions()
            .build());
  }

  public MetadataRewriteInlineSuspendFunctionTest(
      boolean keepOnlyPublic, TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.keepOnlyPublic = keepOnlyPublic;
    this.parameters = parameters;
  }

  private static final KotlinCompileMemoizer libJars =
      getCompileMemoizer(
              getKotlinFileInTest(DescriptorUtils.getBinaryNameFromJavaType(PKG_LIB), "lib"))
          .configure(
              tool -> tool.addClasspathFiles(tool.getCompiler().getKotlinxCoroutinesCoreJar()));

  @Test
  public void smokeTest() throws Exception {
    // no reason to run the smoke test in both configs
    Assume.assumeFalse(keepOnlyPublic);
    Path libJar = libJars.getForConfiguration(kotlinParameters);
    Path output =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion, lambdaGeneration)
            .addClasspathFiles(kotlinc.getKotlinxCoroutinesCoreJar(), libJar)
            .addSourceFiles(
                getKotlinFileInTest(DescriptorUtils.getBinaryNameFromJavaType(PKG_APP), "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();
    testForJvm(parameters)
        .addRunClasspathFiles(
            kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinxCoroutinesCoreJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG_APP + ".MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testMetadataForLib() throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addProgramFiles(libJars.getForConfiguration(kotlinParameters))
            .addClasspathFiles(
                kotlinc.getKotlinStdlibJar(),
                kotlinc.getKotlinAnnotationJar(),
                kotlinc.getKotlinxCoroutinesCoreJar())
            .applyIf(
                keepOnlyPublic,
                builder -> builder.addKeepRules("-keep class * { public <methods>; }"),
                builder -> builder.addKeepAllClassesRule())
            .addKeepAllAttributes()
            .compile()
            .writeToZip();
    Path output =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion, lambdaGeneration)
            .addClasspathFiles(libJar)
            .addSourceFiles(
                getKotlinFileInTest(DescriptorUtils.getBinaryNameFromJavaType(PKG_APP), "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();
    testForJvm(parameters)
        .addRunClasspathFiles(
            kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinxCoroutinesCoreJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG_APP + ".MainKt")
        .assertFailureWithErrorThatThrowsIf(keepOnlyPublic, VerifyError.class)
        .assertSuccessWithOutputIf(!keepOnlyPublic, EXPECTED);
  }
}
