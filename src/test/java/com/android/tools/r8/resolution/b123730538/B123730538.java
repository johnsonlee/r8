// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution.b123730538;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.resolution.b123730538.runner.PublicClassExtender;
import com.android.tools.r8.resolution.b123730538.runner.Runner;
import com.android.tools.r8.resolution.b123730538.sub.PublicClass;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class B123730538 extends TestBase {
  private static final Class<?> MAIN = Runner.class;
  private static List<Path> CLASSES;
  private static final String EXPECTED_OUTPUT = StringUtils.lines("pkg.AbstractClass::foo");

  @Parameter(0)
  public static TestParameters parameters;

  @Parameter(1)
  public static boolean debug;

  @Parameters(name = "{0}, debug = {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllRuntimesAndApiLevels().build(),
        BooleanUtils.values());
  }

  @BeforeClass
  public static void setUpClass() throws Exception {
    CLASSES = ImmutableList.<Path>builder()
        .addAll(ToolHelper.getClassFilesForTestPackage(MAIN.getPackage()))
        .addAll(ToolHelper.getClassFilesForTestPackage(PublicClass.class.getPackage()))
        .build();
  }

  @Test
  public void testProguard() throws Exception {
    assumeFalse(debug);
    assumeTrue(parameters.isCfRuntime());
    Path inJar = temp.newFile("input.jar").toPath().toAbsolutePath();
    writeClassFilesToJar(inJar, CLASSES);
    testForProguard()
        .addProgramFiles(inJar)
        .addKeepMainRule(MAIN)
        .addKeepRules("-dontoptimize")
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(EXPECTED_OUTPUT)
        .inspect(this::inspect);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(CLASSES)
        .addKeepMainRule(MAIN)
        .applyIf(debug, TestCompilerBuilder::debug, b -> b.addKeepRules("-dontoptimize"))
        .setMinApi(parameters)
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(EXPECTED_OUTPUT)
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    MethodSubject foo =
        inspector
            .clazz(PublicClass.class.getTypeName().replace("PublicClass", "AbstractClass"))
            .uniqueMethodWithOriginalName("foo");
    assertThat(foo, debug ? isPresent() : isPresentAndRenamed());

    ClassSubject main = inspector.clazz(PublicClassExtender.class);
    assertThat(main, isPresent());
    MethodSubject methodSubject = main.uniqueMethodWithOriginalName("delegate");
    assertThat(methodSubject, isPresent());

    methodSubject
        .iterateInstructions(InstructionSubject::isInvokeVirtual)
        .forEachRemaining(
            instructionSubject -> {
              String methodName = instructionSubject.getMethod().name.toString();
              if (debug) {
                assertEquals("foo", methodName);
              } else {
                assertNotEquals("foo", methodName);
              }
              assertEquals(foo.getFinalName(), methodName);
            });
  }

}
