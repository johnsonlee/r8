// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk17.enum_sealed;

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
public class SealedClassesEnumJdk17CompiledTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean keepPermittedSubclassesAttribute;

  static final String EXPECTED = StringUtils.lines("A", "a B");

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
        .addInnerClassesAndStrippedOuter(getClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testDesugaring() throws Exception {
    assumeTrue(keepPermittedSubclassesAttribute);
    testForDesugaring(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            c ->
                DesugarTestConfiguration.isNotJavac(c)
                    || parameters.getRuntime().asCf().isNewerThanOrEqual(CfVm.JDK17),
            r -> r.assertSuccessWithOutput(EXPECTED),
            r -> r.assertFailureWithErrorThatThrows(UnsupportedClassVersionError.class));
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(Enum.class);
    assertThat(clazz, isPresentAndRenamed());
    ClassSubject sub1 = inspector.clazz(Enum.B.getClass());
    assertThat(sub1, isPresentAndRenamed());
    assertEquals(
        parameters.isCfRuntime() && keepPermittedSubclassesAttribute
            ? ImmutableList.of(sub1.asTypeSubject())
            : ImmutableList.of(),
        clazz.getFinalPermittedSubclassAttributes());
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    parameters.assumeNoPartialCompilation("TODO");
    testForR8(parameters.getBackend())
        .addInnerClassesAndStrippedOuter(getClass())
        .setMinApi(parameters)
        .applyIf(
            keepPermittedSubclassesAttribute,
            TestShrinkerBuilder::addKeepAttributePermittedSubclasses)
        .addKeepMainRule(Main.class)
        .addKeepClassRulesWithAllowObfuscation(Main.class)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            parameters.isDexRuntime() || parameters.asCfRuntime().isNewerThanOrEqual(CfVm.JDK17),
            r -> r.assertSuccessWithOutput(EXPECTED),
            r -> r.assertFailureWithErrorThatThrows(UnsupportedClassVersionError.class));
  }

  public enum Enum {
    A,
    B {
      @Override
      public String toString() {
        return "a B";
      }
    }
  }

  public class Main {

    public static void main(String[] args) {
      System.out.println(Enum.A);
      System.out.println(Enum.B);
    }
  }
}
