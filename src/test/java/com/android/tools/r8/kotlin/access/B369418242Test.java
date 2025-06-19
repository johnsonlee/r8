// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.access;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.KotlinCompileMemoizer;
import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.kotlin.access.b369418242.pkg.B;
import com.android.tools.r8.kotlin.access.b369418242.pkg.Helper;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class B369418242Test extends KotlinTestBase {

  private static final String PKG = B369418242Test.class.getPackage().getName() + ".b369418242";
  private static final String KOTLIN_FILE = "main";
  private static final String MAIN_CLASS = PKG + ".MainKt";

  private static KotlinCompileMemoizer compiledJars;

  private final TestParameters parameters;

  @BeforeClass
  public static void setup() throws Exception {
    Path classpath = getStaticTemp().newFolder().toPath().resolve("out.jar");
    ZipBuilder.builder(classpath)
        .addFilesRelative(
            ToolHelper.getClassPathForTests(),
            ToolHelper.getClassFileForTestClass(Helper.getClassA()),
            ToolHelper.getClassFileForTestClass(B.class))
        .build();
    compiledJars =
        getCompileMemoizer(
                Paths.get(
                    ToolHelper.TESTS_DIR,
                    "java",
                    DescriptorUtils.getBinaryNameFromJavaType(PKG),
                    KOTLIN_FILE + FileUtils.KT_EXTENSION))
            .configure(x -> x.addClasspathFiles(classpath));
  }

  @Parameters(name = "{0}, {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters()
            .withAllCompilers()
            .withAllTargetVersions()
            .withLambdaGenerationInvokeDynamic()
            .build());
  }

  public B369418242Test(TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  @Test
  public void testKotlinExampleOnJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramFiles(compiledJars.getForConfiguration(kotlinParameters))
        .addProgramFiles(kotlinc.getKotlinStdlibJar())
        .addProgramClasses(Helper.getClassA(), B.class)
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertFailureWithErrorThatThrows(IllegalAccessError.class);
  }

  @Test
  public void testKotlinExampleWithD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addProgramFiles(compiledJars.getForConfiguration(kotlinParameters))
        .addProgramFiles(kotlinc.getKotlinStdlibJar())
        .addProgramClasses(Helper.getClassA(), B.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertFailureWithErrorThatThrows(IllegalAccessError.class);
  }

  @Test
  public void testKotlinExampleWithR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(compiledJars.getForConfiguration(kotlinParameters))
        .addProgramFiles(kotlinc.getKotlinStdlibJar())
        .addProgramClasses(Helper.getClassA(), B.class)
        .addKeepMainRule(MAIN_CLASS)
        .addDontWarn("org.jetbrains.annotations.NotNull")
        .setMinApi(parameters)
        .run(parameters.getRuntime(), MAIN_CLASS)
        // TODO(b/369418242): R8 should in principle preserve the IAE.
        .assertSuccessWithEmptyOutput();
  }

  @Test
  public void testJavaExampleOnJVM() throws Exception {
    parameters.assumeJvmTestParameters();
    assumeTrue(kotlinParameters.isFirst());
    testForJvm(parameters)
        .addProgramClasses(Main.class, Helper.getClassA(), B.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithEmptyOutput();
  }

  public static class Main {

    public static void main(String[] args) {
      B child = new B();
      child.doSomething(new B());
    }
  }
}
