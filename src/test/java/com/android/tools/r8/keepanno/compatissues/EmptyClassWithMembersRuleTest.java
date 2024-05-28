// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.compatissues;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.ProguardVersion;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.ThrowableConsumer;
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
    R8,
    R8_COMPAT;

    public boolean isPG() {
      return this == PG;
    }

    public boolean isCompat() {
      return this == R8_COMPAT;
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

  private TestRunResult<?> runTest(
      ThrowableConsumer<TestShrinkerBuilder<?, ?, ?, ?, ?>> configuration) throws Exception {
    TestShrinkerBuilder<?, ?, ?, ?, ?> builder;
    if (shrinker.isPG()) {
      builder =
          testForProguard(ProguardVersion.getLatest()).addDontWarn(getClass()).apply(configuration);
    } else {
      builder = testForR8Compat(parameters.getBackend(), shrinker.isCompat()).apply(configuration);
    }
    return builder
        .addProgramClassFileData(
            transformer(A.class)
                .removeMethods(MethodPredicate.all())
                .removeFields(FieldPredicate.all())
                .transform())
        .addProgramClasses(TestClass.class)
        .addKeepMainRule(TestClass.class)
        .run(parameters.getRuntime(), TestClass.class);
  }

  @Test
  public void testNoKeep() throws Exception {
    runTest(
            builder ->
                builder
                    .addKeepRules("-keep class missingClassToForceUnusedRuleDiagnostic")
                    .applyIfR8(R8TestBuilder::allowUnusedProguardConfigurationRules))
        .assertSuccessWithOutput(EXPECTED_RENAMED)
        .inspect(inspector -> assertThat(inspector.clazz(A.class), isPresentAndRenamed()));
  }

  @Test
  public void testNoMembers() throws Exception {
    runTest(
            builder ->
                builder.addKeepRules("-keepclasseswithmembers class " + typeName(A.class) + " {}"))
        .assertSuccessWithOutput(EXPECTED_KEPT)
        .inspect(inspector -> assertThat(inspector.clazz(A.class), isPresentAndNotRenamed()));
  }

  @Test
  public void testAllMembers() throws Exception {
    // Surprising, but consistent for PG and R8, "any member" pattern does not match an empty class.
    runTest(
            builder ->
                builder
                    .addKeepRules("-keepclasseswithmembers class " + typeName(A.class) + " { *; }")
                    .applyIfR8(R8TestBuilder::allowUnusedProguardConfigurationRules))
        .assertSuccessWithOutput(EXPECTED_RENAMED)
        .inspect(inspector -> assertThat(inspector.clazz(A.class), isPresentAndRenamed()));
  }

  @Test
  public void testAllMethods() throws Exception {
    runTest(
            builder ->
                builder
                    .addKeepRules(
                        "-keepclasseswithmembers class " + typeName(A.class) + " { *** *(...); }")
                    .applyIfR8(R8TestBuilder::allowUnusedProguardConfigurationRules))
        .assertSuccessWithOutput(EXPECTED_RENAMED)
        .inspect(inspector -> assertThat(inspector.clazz(A.class), isPresentAndRenamed()));
  }

  @Test
  public void testAllMethods2() throws Exception {
    runTest(
            builder ->
                builder
                    .addKeepRules(
                        "-keepclasseswithmembers class " + typeName(A.class) + " { <methods>; }")
                    .applyIfR8(R8TestBuilder::allowUnusedProguardConfigurationRules))
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
