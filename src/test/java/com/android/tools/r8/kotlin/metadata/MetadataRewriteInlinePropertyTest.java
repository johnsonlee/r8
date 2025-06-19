// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata;

import com.android.tools.r8.KotlinCompileMemoizer;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewriteInlinePropertyTest extends KotlinMetadataTestBase {

  private final String EXPECTED = StringUtils.lines("true", "false", "false", "true");

  @Parameterized.Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(),
        getKotlinTestParameters().withAllCompilersAndLambdaGenerations().build());
  }

  public MetadataRewriteInlinePropertyTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  private static final KotlinCompileMemoizer libJars =
      getCompileMemoizer(getKotlinFileInTest(PKG_PREFIX + "/inline_property_lib", "lib"));
  private final TestParameters parameters;

  @Test
  public void smokeTest() throws Exception {
    Path libJar = libJars.getForConfiguration(kotlinParameters);
    Path output =
        kotlinc(getKotlincHostRuntime(parameters.getRuntime()), kotlinParameters)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(PKG_PREFIX + "/inline_property_app", "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();
    testForJvm(parameters)
        .addRunClasspathFiles(kotlinc.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG + ".inline_property_app.MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testMetadataForLib() throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addClasspathFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinAnnotationJar())
            .addProgramFiles(libJars.getForConfiguration(kotlinParameters))
            // Allow renaming A to ensure that we rename in the flexible upper bound type.
            .addKeepAllClassesRule()
            .addKeepAttributes(
                ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS,
                ProguardKeepAttributes.SIGNATURE,
                ProguardKeepAttributes.INNER_CLASSES,
                ProguardKeepAttributes.ENCLOSING_METHOD)
            .compile()
            .inspect(
                inspector ->
                    assertEqualDeserializedMetadata(
                        inspector,
                        new CodeInspector(libJars.getForConfiguration(kotlinParameters))))
            .writeToZip();
    Path main =
        kotlinc(parameters.getRuntime().asCf(), kotlinParameters)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(PKG_PREFIX + "/inline_property_app", "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();
    testForJvm(parameters)
        .addRunClasspathFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinReflectJar(), libJar)
        .addClasspath(main)
        .run(parameters.getRuntime(), PKG + ".inline_property_app.MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }
}
