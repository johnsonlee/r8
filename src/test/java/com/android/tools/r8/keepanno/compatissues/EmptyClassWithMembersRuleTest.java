// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.compatissues;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.ProguardVersion;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.transformers.ClassFileTransformer.FieldPredicate;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import com.android.tools.r8.utils.StringUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

@RunWith(Parameterized.class)
public class EmptyClassWithMembersRuleTest extends TestBase {

  static final String EXPECTED_RENAMED = StringUtils.lines("a");
  static final String EXPECTED_KEPT = StringUtils.lines("A");

  public enum Shrinker {
    PG,
    R8;

    public boolean isPG() {
      return this == PG;
    }
  }

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public Shrinker shrinker;

  @Parameterized.Parameters(name = "{0}, {1}")
  public static List<Object[]> data() {
    return buildParameters(getTestParameters().withDefaultCfRuntime().build(), Shrinker.values());
  }

  private TestRunResult<?> runTest(String... keepRules) throws Exception {
    TestShrinkerBuilder<?, ?, ?, ?, ?> builder;
    if (shrinker.isPG()) {
      builder = testForProguard(ProguardVersion.getLatest()).addDontWarn(getClass());
    } else {
      builder = testForR8(parameters.getBackend()).allowUnusedProguardConfigurationRules();
    }
    return builder
        .addProgramClassFileData(
            transformer(A.class)
                .removeMethods(MethodPredicate.all())
                .removeFields(FieldPredicate.all())
                .transform())
        .addProgramClasses(TestClass.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules(keepRules)
        .run(parameters.getRuntime(), TestClass.class);
  }

  @Test
  public void testNoKeep() throws Exception {
    runTest("-keep class missingClassToForceUnusedRuleDiagnostic")
        .assertSuccessWithOutput(EXPECTED_RENAMED)
        .inspect(inspector -> assertThat(inspector.clazz(A.class), isPresentAndRenamed()));
  }

  @Test
  public void testNoMembers() throws Exception {
    // TODO(b/323136645): This is incorrect behavior for R8.
    //  R8 implicitly replaces the member rule with a member rule for <init>() which does not match.
    runTest("-keepclasseswithmembers class " + typeName(A.class) + " { }")
        .assertSuccessWithOutput(shrinker.isPG() ? EXPECTED_KEPT : EXPECTED_RENAMED)
        .inspect(
            inspector ->
                assertThat(
                    inspector.clazz(A.class),
                    shrinker.isPG() ? isPresentAndNotRenamed() : isPresentAndRenamed()));
  }

  @Test
  public void testAllMembers() throws Exception {
    // Surprising, but consistent for PG and R8, "any member" pattern does not match an empty class.
    runTest("-keepclasseswithmembers class " + typeName(A.class) + " { *; }")
        .assertSuccessWithOutput(EXPECTED_RENAMED)
        .inspect(inspector -> assertThat(inspector.clazz(A.class), isPresentAndRenamed()));
  }

  @Test
  public void testAllMethods() throws Exception {
    runTest("-keepclasseswithmembers class " + typeName(A.class) + " { *** *(...); }")
        .assertSuccessWithOutput(EXPECTED_RENAMED)
        .inspect(inspector -> assertThat(inspector.clazz(A.class), isPresentAndRenamed()));
  }

  @Test
  public void testAllMethods2() throws Exception {
    runTest("-keepclasseswithmembers class " + typeName(A.class) + " { <methods>; }")
        .assertSuccessWithOutput(EXPECTED_RENAMED)
        .inspect(inspector -> assertThat(inspector.clazz(A.class), isPresentAndRenamed()));
  }

  public static class A {
    // Completely empty via transformer.
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(A.class.getTypeName().substring(A.class.getTypeName().length() - 1));
    }
  }
}
