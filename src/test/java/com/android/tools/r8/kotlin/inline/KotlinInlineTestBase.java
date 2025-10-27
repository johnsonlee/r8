// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.inline;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.KotlinCompileMemoizer;
import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.Test;
import org.junit.runners.Parameterized;

public abstract class KotlinInlineTestBase extends KotlinTestBase {

  static final String PKG =
      com.android.tools.r8.kotlin.inline.KotlinInlineTestBase.class.getPackage().getName();
  static final String PKG_PREFIX = DescriptorUtils.getBinaryNameFromJavaType(PKG);

  protected String subPackage;
  protected KotlinCompileMemoizer jarMap;

  protected TestParameters parameters;

  @Parameterized.Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(),
        getKotlinTestParameters().withAllCompilersAndLambdaGenerations().build());
  }

  public KotlinInlineTestBase(KotlinTestParameters kotlinParameters, String subPackage) {
    super(kotlinParameters);
    this.subPackage = subPackage;
    this.jarMap = getCompileMemoizer(getLibSourceFile());
  }

  protected Path getLibSourceFile() {
    return getKotlinFileInTest(PKG_PREFIX + "/" + subPackage + "/lib", "lib");
  }

  protected Path getAppSourceFile() {
    return getKotlinFileInTest(PKG_PREFIX + "/" + subPackage + "/app", "main");
  }

  protected String getMainClass() {
    return PKG + "." + subPackage + ".app.MainKt";
  }

  protected abstract String getExpected();

  protected abstract String getLibraryClass();

  protected abstract void configure(R8FullTestBuilder builder);

  protected boolean kotlinCompilationFails() {
    return false;
  }

  protected void kotlinCompilationResult(ProcessResult result) {}

  @Test
  public void smokeTest() throws Exception {
    Path libJar = jarMap.getForConfiguration(kotlinParameters);
    Path output =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion, lambdaGeneration)
            .addClasspathFiles(libJar)
            .addSourceFiles(getAppSourceFile())
            .compile();
    testForRuntime(parameters)
        .addProgramFiles(
            libJar, output, kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinReflectJar())
        .run(parameters.getRuntime(), getMainClass())
        .assertSuccessWithOutput(getExpected());
  }

  @Test
  public void testR8Library() throws Exception {
    Path r8LibJar =
        testForR8(parameters.getBackend())
            .addClasspathFiles(kotlinc.getKotlinAnnotationJar())
            .addProgramFiles(jarMap.getForConfiguration(kotlinParameters))
            .addClasspathFiles(kotlinc.getKotlinStdlibJar())
            .addKeepKotlinMetadata()
            .apply(this::configure)
            .compile()
            .inspect(
                inspector -> {
                  ClassSubject libKtClass = inspector.clazz(getLibraryClass());
                  assertThat(libKtClass, isPresentAndNotRenamed());
                  libKtClass
                      .allMethods()
                      .forEach(method -> assertThat(method, isPresentAndNotRenamed()));
                })
            .writeToZip();
    Path output =
        kotlinc(parameters.getRuntime().asCf(), kotlinParameters)
            .addClasspathFiles(r8LibJar)
            .addSourceFiles(getAppSourceFile())
            .setOutputPath(temp.newFolder().toPath())
            .compile(kotlinCompilationFails(), this::kotlinCompilationResult);
    if (!kotlinCompilationFails()) {
      testForRuntime(parameters)
          .addProgramFiles(
              r8LibJar, output, kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinReflectJar())
          .run(parameters.getRuntime(), getMainClass())
          .assertSuccessWithOutput(getExpected());
    }
  }
}
