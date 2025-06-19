// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.inliner.conditionalsimpleinlining;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.KotlinCompileMemoizer;
import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SwitchWithSimpleCasesInliningKotlinTest extends KotlinTestBase {

  private final TestParameters parameters;
  private static final String EXPECTED_OUTPUT =
      StringUtils.lines(
          "true", "false", "true", "true", "false", "true", "false", "false", "false", "false",
          "true", "true", "false", "true", "false");
  private static final String PKG =
      SwitchWithSimpleCasesInliningKotlinTest.class.getPackage().getName();
  private static final KotlinCompileMemoizer compiledJars =
      getCompileMemoizer(
          Paths.get(
              ToolHelper.TESTS_DIR,
              "java",
              DescriptorUtils.getBinaryNameFromJavaType(PKG),
              "SwitchWithSimpleCasesInliningKotlin" + FileUtils.KT_EXTENSION));

  @Parameters(name = "{0}, {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters().withAllCompilersAndLambdaGenerations().build());
  }

  public SwitchWithSimpleCasesInliningKotlinTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    String mainClass = PKG + ".SwitchWithSimpleCasesInliningKotlinKt";
    testForR8(parameters.getBackend())
        .addProgramFiles(compiledJars.getForConfiguration(kotlinParameters))
        .addProgramFiles(kotlinc.getKotlinStdlibJar())
        .addProgramFiles(kotlinc.getKotlinAnnotationJar())
        .addKeepMainRule(mainClass)
        .enableProguardTestOptions()
        .addKeepRules(
            "-neverinline class "
                + PKG
                + ".SwitchWithSimpleCasesInliningKotlinKt"
                + "{ hasProperty(...); }")
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject mainClassSubject = inspector.clazz(mainClass);
              assertThat(mainClassSubject, isPresent());
              MethodSubject mainMethodSubject = mainClassSubject.mainMethod();
              assertThat(mainMethodSubject, isPresent());
              // TODO(b/390168887): Add support.
              assertFalse(
                  mainMethodSubject
                      .streamInstructions()
                      .filter(InstructionSubject::isConstString)
                      .allMatch(i -> i.isConstString("O")));
            })
        .run(parameters.getRuntime(), mainClass)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }
}
