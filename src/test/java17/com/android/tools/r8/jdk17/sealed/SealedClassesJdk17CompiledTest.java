// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk17.sealed;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static junit.framework.Assert.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.DesugarTestConfiguration;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SealedClassesJdk17CompiledTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean keepPermittedSubclassesAttribute;

  static final String EXPECTED = StringUtils.lines("R8 compiler", "D8 compiler");

  @Parameters(name = "{0}, keepPermittedSubclasses = {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withAllRuntimes()
            .withAllApiLevelsAlsoForCf()
            .withPartialCompilation()
            .build(),
        BooleanUtils.values());
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    assumeTrue(keepPermittedSubclassesAttribute);
    assumeTrue(parameters.asCfRuntime().isNewerThanOrEqual(CfVm.JDK17));
    testForJvm(parameters)
        .addProgramClassesAndInnerClasses(Helper.getSealedClasses())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testDesugaring() throws Exception {
    assumeTrue(keepPermittedSubclassesAttribute);
    testForDesugaring(parameters)
        .addProgramClassesAndInnerClasses(Helper.getSealedClasses())
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            c ->
                DesugarTestConfiguration.isNotJavac(c)
                    || parameters.getRuntime().asCf().isNewerThanOrEqual(CfVm.JDK17),
            r -> r.assertSuccessWithOutput(EXPECTED),
            r -> r.assertFailureWithErrorThatThrows(UnsupportedClassVersionError.class));
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(Compiler.class);
    assertThat(clazz, isPresentAndRenamed());
    ClassSubject sub1 = inspector.clazz(R8Compiler.class);
    ClassSubject sub2 = inspector.clazz(D8Compiler.class);
    assertThat(sub1, isPresentAndRenamed());
    assertThat(sub2, isPresentAndRenamed());
    assertEquals(
        hasSealedClassesSupport(parameters) && keepPermittedSubclassesAttribute
            ? ImmutableList.of(sub1.asTypeSubject(), sub2.asTypeSubject())
            : ImmutableList.of(),
        clazz.getFinalPermittedSubclassAttributes());
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    assumeTrue(
        parameters.isDexRuntime() || parameters.asCfRuntime().isNewerThanOrEqual(CfVm.JDK17));
    parameters.assumeNoPartialCompilation("TODO");
    testForR8(parameters.getBackend())
        .addProgramClassesAndInnerClasses(Helper.getSealedClasses())
        .setMinApi(parameters)
        .applyIf(
            keepPermittedSubclassesAttribute,
            TestShrinkerBuilder::addKeepAttributePermittedSubclasses)
        .addKeepPermittedSubclasses(Compiler.class)
        .addKeepRules("-keep,allowobfuscation class * extends " + Compiler.class.getTypeName())
        .addKeepMainRule(Main.class)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            parameters.isDexRuntime() || parameters.asCfRuntime().isNewerThanOrEqual(CfVm.JDK17),
            r -> r.assertSuccessWithOutput(EXPECTED),
            r -> r.assertFailureWithErrorThatThrows(UnsupportedClassVersionError.class));
  }
}
