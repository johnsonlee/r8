// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.MIN_SUPPORTED_VERSION;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.KotlinCompileMemoizer;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewriteInNamedArgumentsTest extends KotlinMetadataTestBase {
  static final String LIB_LOCATION = PKG_PREFIX + "/named_arguments_lib";
  static final String APP_LOCATION = PKG_PREFIX + "/named_arguments_app";
  static final String MAIN_CLASS = PKG + ".named_arguments_app.MainKt";
  private static final String EXPECTED = StringUtils.lines("1 + 2 + 3", "3 + 2 + 1");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(),
        getKotlinTestParameters()
            .withCompilersStartingFromIncluding(MIN_SUPPORTED_VERSION)
            .withAllLambdaGenerations()
            .withAllTargetVersions()
            .build());
  }

  public MetadataRewriteInNamedArgumentsTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  private static final KotlinCompileMemoizer libJarMap =
      getCompileMemoizer(getKotlinFileInTest(LIB_LOCATION, "lib"));

  @Test
  public void smokeTest() throws Exception {
    Path libJar = libJarMap.getForConfiguration(kotlinParameters);

    Path output =
        kotlinc(parameters.getRuntime().asCf(), kotlinParameters)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(APP_LOCATION, "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();

    testForJvm(parameters)
        .addRunClasspathFiles(kotlinc.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void test() throws Exception {
    runTest(false);
  }

  @Test
  public void testDontShrink() throws Exception {
    runTest(true);
  }

  public void runTest(boolean dontShrink) throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addClasspathFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinAnnotationJar())
            .addProgramFiles(libJarMap.getForConfiguration(kotlinParameters))
            .addKeepRules("-keep class **.Lib { <methods>; }")
            .applyIf(dontShrink, TestShrinkerBuilder::addDontShrink)
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .compile()
            .writeToZip();

    Path output =
        kotlinc(parameters.getRuntime().asCf(), kotlinParameters)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(APP_LOCATION, "main"))
            .setOutputPath(temp.newFolder().toPath())
            // TODO(b/426163872): -dontshrink should not remove metadata.
            .compile(
                dontShrink,
                result -> {
                  if (dontShrink) {
                    assertThat(
                        result.stderr,
                        containsString("named arguments are prohibited for non-Kotlin functions"));
                  }
                });
    if (dontShrink) {
      return;
    }

    testForJvm(parameters)
        .addRunClasspathFiles(kotlinc.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutput(EXPECTED);
  }
}
