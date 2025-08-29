// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk21.backport;

import static com.android.tools.r8.utils.FileUtils.CLASS_EXTENSION;
import static com.android.tools.r8.utils.FileUtils.JAVA_EXTENSION;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Jdk21Float16Test extends TestBase {

  private static final String FLOAT_16_TEST_NAME = "Binary16Conversion";
  private static final String FLOAT_16_NAN_TEST_NAME = "Binary16ConversionNaN";

  private static Path getTestPath(String name) {
    return Paths.get(ToolHelper.THIRD_PARTY_DIR, "openjdk", "float16-test", name + JAVA_EXTENSION);
  }

  private static final Path[] JDK_21_FLOAT_16_TEST_JAVA_FILES =
      new Path[] {getTestPath(FLOAT_16_TEST_NAME), getTestPath(FLOAT_16_NAN_TEST_NAME)};

  private static Path[] JDK_21_FLOAT_16_TEST_CLASS_FILES;

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesAndAllApiLevels().withCfRuntime(CfVm.JDK21).build();
  }

  @BeforeClass
  public static void compileTestClasses() throws Exception {
    // Build test constants.
    Path output = getStaticTemp().newFolder("output").toPath();
    javac(TestRuntime.getCheckedInJdk21(), getStaticTemp())
        .addSourceFiles(JDK_21_FLOAT_16_TEST_JAVA_FILES)
        .setOutputPath(output)
        .compile();
    JDK_21_FLOAT_16_TEST_CLASS_FILES =
        new Path[] {
          output.resolve(FLOAT_16_TEST_NAME + CLASS_EXTENSION),
          output.resolve(FLOAT_16_TEST_NAME + "$Binary16" + CLASS_EXTENSION),
          output.resolve(FLOAT_16_NAN_TEST_NAME + CLASS_EXTENSION)
        };
  }

  @Test
  public void testFloat16() throws Exception {
    testForD8(parameters)
        .addProgramFiles(JDK_21_FLOAT_16_TEST_CLASS_FILES)
        .run(parameters.getRuntime(), FLOAT_16_TEST_NAME)
        .assertSuccessWithOutput("");
  }

  @Test
  public void testFloat16NaN() throws Exception {
    Assume.assumeTrue("The test fails also on jdk21", false);
    testForD8(parameters)
        .addProgramFiles(JDK_21_FLOAT_16_TEST_CLASS_FILES)
        .run(parameters.getRuntime(), FLOAT_16_NAN_TEST_NAME)
        .assertSuccessWithOutput("");
  }
}
