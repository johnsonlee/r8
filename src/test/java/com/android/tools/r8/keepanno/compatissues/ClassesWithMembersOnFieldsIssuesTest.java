// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.compatissues;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.ProguardVersion;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.utils.StringUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

@RunWith(Parameterized.class)
public class ClassesWithMembersOnFieldsIssuesTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("42");

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
      assumeTrue(parameters.isCfRuntime());
      builder = testForProguard(ProguardVersion.getLatest()).addDontWarn(getClass());
    } else {
      builder = testForR8(parameters.getBackend());
    }
    return builder
        .addProgramClasses(A.class, TestClass.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules(keepRules)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testNoKeep() throws Exception {
    runTest()
        .inspect(
            inspector ->
                assertThat(
                    inspector.clazz(A.class).uniqueFieldWithOriginalName("x"),
                    isPresentAndRenamed()));
  }

  @Test
  public void testAllMembers() throws Exception {
    runTest("-keepclasseswithmembers class " + typeName(A.class) + " { *; }")
        .inspect(
            inspector ->
                assertThat(
                    inspector.clazz(A.class).uniqueFieldWithOriginalName("x"),
                    // Note that the rule does pin the name in both shrinkers unlike for methods.
                    isPresentAndNotRenamed()));
  }

  @Test
  public void testAllFields() throws Exception {
    runTest("-keepclasseswithmembers class " + typeName(A.class) + " { *** *; }")
        .inspect(
            inspector ->
                assertThat(
                    inspector.clazz(A.class).uniqueFieldWithOriginalName("x"),
                    isPresentAndNotRenamed()));
  }

  public abstract static class A {

    public static int x = 0;
  }

  static class TestClass {

    public static void main(String[] args) {
      A.x += System.nanoTime() > 0 ? args.length + 42 : 0;
      A.x += System.nanoTime() < 0 ? args.length + 42 : 0;
      System.out.println(A.x);
    }
  }
}
