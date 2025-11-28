// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.KOTLINC_2_2_0;
import static com.android.tools.r8.KotlinCompilerTool.KotlinTargetVersion.JAVA_8;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.KotlinCompileMemoizer;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewriteContextParametersTest extends KotlinMetadataTestBase {

  private static final String PKG_LIB = PKG + ".context_parameters_lib";
  private static final String PKG_APP = PKG + ".context_parameters_app";
  private static final Path LIB_FILE =
      getFileInTest(PKG_PREFIX + "/context_parameters_lib", "lib.txt");
  private static final Path MAIN_FILE =
      getFileInTest(PKG_PREFIX + "/context_parameters_app", "main.txt");
  private static final String MAIN = PKG_APP + ".MainKt";
  private final TestParameters parameters;

  private static final String EXPECTED =
      StringUtils.lines(
          "FooImpl::m1",
          "BarImpl::m2",
          "BazImpl::m3",
          "BazImpl::m3",
          "FooImpl::m1",
          "BarImpl::m2",
          "Hello World!");

  @Parameterized.Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(),
        getKotlinTestParameters()
            .withCompilersStartingFromIncluding(KOTLINC_2_2_0)
            .withAllLambdaGenerations()
            .withTargetVersion(JAVA_8)
            .build());
  }

  public MetadataRewriteContextParametersTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  private static final KotlinCompileMemoizer libJars =
      getCompileMemoizer()
          .configure(
              kotlinc ->
                  kotlinc
                      .addSourceFilesWithNonKtExtension(getStaticTemp(), LIB_FILE)
                      .enableExperimentalContextParameters());

  @Test
  public void smokeTest() throws Exception {
    Path output =
        kotlinc(parameters.getRuntime().asCf(), kotlinParameters)
            .addClasspathFiles(libJars.getForConfiguration(kotlinParameters))
            .addSourceFilesWithNonKtExtension(temp, MAIN_FILE)
            .setOutputPath(temp.newFolder().toPath())
            .enableExperimentalContextParameters()
            .compile();
    testForJvm(parameters)
        .addRunClasspathFiles(
            kotlinc.getKotlinStdlibJar(),
            kotlinc.getKotlinReflectJar(),
            libJars.getForConfiguration(kotlinParameters))
        .addClasspath(output)
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testMetadataForCompilationWithKeepAll() throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addClasspathFiles(kotlinc.getKotlinAnnotationJar())
            .addProgramFiles(
                libJars.getForConfiguration(kotlinParameters), kotlinc.getKotlinStdlibJar())
            .addKeepClassAndMembersRules(PKG_LIB + ".*")
            .addKeepAttributes(
                ProguardKeepAttributes.SIGNATURE,
                ProguardKeepAttributes.INNER_CLASSES,
                ProguardKeepAttributes.ENCLOSING_METHOD)
            .addKeepKotlinMetadata()
            .addOptionsModification(
                options -> options.testing.keepMetadataInR8IfNotRewritten = false)
            .compile()
            // Since this has a keep-all classes rule assert that the meta-data is equal to the
            // original one.
            .inspect(
                inspector ->
                    assertEqualDeserializedMetadata(
                        inspector,
                        new CodeInspector(libJars.getForConfiguration(kotlinParameters))))
            .writeToZip();
    Path main =
        kotlinc(parameters.getRuntime().asCf(), kotlinParameters)
            .addClasspathFiles(libJar)
            .addSourceFilesWithNonKtExtension(temp, MAIN_FILE)
            .setOutputPath(temp.newFolder().toPath())
            .enableExperimentalContextParameters()
            .compile();
    testForJvm(parameters)
        .addRunClasspathFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinReflectJar(), libJar)
        .addClasspath(main)
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testMetadataInExtensionFunction_renamedKotlinSources() throws Exception {
    R8TestCompileResult r8LibraryResult =
        testForR8(parameters.getBackend())
            .addClasspathFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinAnnotationJar())
            .addProgramFiles(libJars.getForConfiguration(kotlinParameters))
            // Ensure that we do not rename members
            .addKeepRules("-keepclassmembers class * { *; }")
            // Keep the Foo class but rename it.
            .addKeepRules("-keep,allowobfuscation class " + PKG_LIB + ".Foo")
            // Keep the Bar class but rename it.
            .addKeepRules("-keep,allowobfuscation class " + PKG_LIB + ".Bar")
            // Keep the Baz class but rename it.
            .addKeepRules("-keep,allowobfuscation class " + PKG_LIB + ".Baz")
            // Keep all Printer fields.
            .addKeepRules("-keep class " + PKG_LIB + ".Printer { *; }")
            // Keep Super, but allow minification.
            .addKeepRules("-keep class " + PKG_LIB + ".LibKt { <methods>; }")
            .addKeepRuntimeVisibleAnnotations()
            .compile();

    // Rewrite the kotlin source to rewrite the classes from the mapping file
    String kotlinSource = FileUtils.readTextFile(MAIN_FILE, StandardCharsets.UTF_8);

    CodeInspector inspector = r8LibraryResult.inspector();
    // Rewrite the source kotlin file that reference the renamed classes uses in the context
    // parameters.
    for (String className : new String[] {"Foo", "Bar", "Baz"}) {
      String originalClassName = PKG_LIB + "." + className;
      ClassSubject clazz = inspector.clazz(originalClassName);
      assertThat(clazz, isPresentAndRenamed());
      kotlinSource =
          kotlinSource.replace("import " + originalClassName, "import " + clazz.getFinalName());
      kotlinSource =
          kotlinSource.replace(
              ": " + className + " {",
              ": "
                  + DescriptorUtils.getSimpleClassNameFromDescriptor(clazz.getFinalDescriptor())
                  + " {");
    }

    Path newSource = temp.newFolder().toPath().resolve("main.kt");
    Files.write(newSource, kotlinSource.getBytes(StandardCharsets.UTF_8));

    Path libJar = r8LibraryResult.writeToZip();
    Path output =
        kotlinc(parameters.getRuntime().asCf(), kotlinParameters)
            .addClasspathFiles(libJar)
            .addSourceFiles(newSource)
            .setOutputPath(temp.newFolder().toPath())
            .enableExperimentalContextParameters()
            .compile();

    testForJvm(parameters)
        .addRunClasspathFiles(kotlinc.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(EXPECTED);
  }
}
