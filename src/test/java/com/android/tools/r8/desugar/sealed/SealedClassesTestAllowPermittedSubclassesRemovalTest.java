// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.sealed;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static junit.framework.Assert.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.Matchers;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SealedClassesTestAllowPermittedSubclassesRemovalTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  static final String EXPECTED = StringUtils.lines("Sub1", "Sub2");

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK17)
        .withDexRuntimes()
        .withAllApiLevels()
        .withPartialCompilation()
        .build();
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(Super.class);
    assertThat(clazz, Matchers.isPresentAndNotRenamed());
    ClassSubject sub1 = inspector.clazz(Sub1.class);
    ClassSubject sub2 = inspector.clazz(Sub2.class);
    assertThat(sub1, isPresentAndRenamed());
    assertThat(sub2, isPresentAndRenamed());
    assertEquals(
        parameters.hasSealedClassesSupport()
            ? ImmutableList.of(sub1.asTypeSubject(), sub2.asTypeSubject())
            : ImmutableList.of(),
        clazz.getFinalPermittedSubclassAttributes());
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addProgramClasses(TestClass.class, Sub1.class, Sub2.class)
        .addProgramClassFileData(getTransformedClasses())
        .addKeepAttributePermittedSubclasses()
        .addKeepRules("-keep,allowpermittedsubclassesremoval class " + Super.class.getTypeName())
        .addKeepClassRulesWithAllowObfuscation(Sub1.class, Sub2.class)
        .addKeepMainRule(TestClass.class)
        .compile()
        .inspectIf(!parameters.isRandomPartialCompilation(), this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  public byte[] getTransformedClasses() throws Exception {
    return transformer(Super.class)
        .setPermittedSubclasses(Super.class, Sub1.class, Sub2.class)
        .transform();
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(new Sub1());
      System.out.println(new Sub2());
    }
  }

  abstract static class Super /* permits Sub1, Sub2 */ {}

  static class Sub1 extends Super {

    @Override
    public String toString() {
      return "Sub1";
    }
  }

  static class Sub2 extends Super {

    @Override
    public String toString() {
      return "Sub2";
    }
  }
}
