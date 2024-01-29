// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.compatissues;

import static com.android.tools.r8.utils.codeinspector.AssertUtils.assertThrowsIf;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
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
public class BackReferenceIssuesTest extends TestBase {

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
  public void testMissingReturnTypeBackref() throws Exception {
    runTest(
            "-if class **$A {",
            "  *** *(int);",
            "}",
            "-keepclassmembers class <1>$A {",
            // This should have been `<2> <3>(int);` but wildcard on the return type is gone.
            // TODO(b/322114141): Update this rule if fixed in future version of PG.
            "  *** <2>(int);",
            "}")
        .inspect(
            inspector ->
                assertThat(
                    inspector.clazz(A.class).uniqueMethodWithOriginalName("foo"),
                    // The rule is not valid and does not keep the method in R8.
                    shrinker.isPG() ? isPresentAndNotRenamed() : isPresentAndRenamed()));
  }

  @Test
  public void testMissingMethodParameterBackref() throws Exception {
    runTest(
            "-if class **$A {",
            "  void foo(***);",
            "}",
            "-keepclassmembers class <1>$A {",
            "  void foo(<2>);",
            "}")
        .inspect(
            inspector ->
                assertThat(
                    inspector.clazz(A.class).uniqueMethodWithOriginalName("foo"),
                    // The above rule should have retained foo.
                    // TODO(b/322114141): Update the status if fixed in future version of PG.
                    shrinker.isPG() ? isAbsent() : isPresentAndNotRenamed()));
  }

  @Test
  public void testMissingMethodAnyParametersBackref() throws Exception {
    // TODO(b/265892343): R8 will throw when parsing a backref to `...`
    assertThrowsIf(
        !shrinker.isPG(),
        CompilationFailedException.class,
        () ->
            runTest(
                    "-if class **$A {",
                    "  void foo(...);",
                    "}",
                    "-keepclassmembers class <1>$A {",
                    "  void foo(<2>);",
                    "}")
                .inspect(
                    inspector ->
                        assertThat(
                            inspector.clazz(A.class).uniqueMethodWithOriginalName("foo"),
                            // The above rule should have retained foo.
                            // TODO(b/322114141): Update the status if fixed in future version of
                            // PG.
                            isAbsent())));
  }

  @Test
  public void testMissingFieldTypeBackref() throws Exception {
    runTest(
            "-if class **$A {",
            "  *** x;",
            "}",
            // Just a comment to avoid a one-line formatting.
            "-keepclassmembers class <1>$A {",
            "  <2> y;",
            "}")
        .inspect(
            inspector ->
                assertThat(
                    inspector.clazz(A.class).uniqueFieldWithOriginalName("y"),
                    // TODO(b/322114141): Update this status if fixed in future version of PG.
                    // TODO(b/322910135): R8 should not throw away a kept static final field.
                    isAbsent()));
  }

  @Test
  public void testMissingFieldTypeBackrefPrefixPg() throws Exception {
    runTest(
            "-if class **$A {",
            "  !static *** *;",
            "}",
            "-keepclassmembers class <1>$A {",
            // This should have been `<2> <3>y;` but the field type is skipped.
            // TODO(b/322114141): Update this rule if fixed in future version of PG.
            "  *** <2>y;",
            "}")
        .inspect(
            inspector ->
                assertThat(
                    inspector.clazz(A.class).uniqueFieldWithOriginalName("xy"),
                    // The rule does not match with R8.
                    shrinker.isPG() ? isPresentAndNotRenamed() : isAbsent()));
  }

  @Test
  public void testTypeRefInMember() throws Exception {
    runTest(
            "-if class **$A {",
            "  " + typeName(A.class) + " *(" + typeName(A.class) + ");",
            "}",
            "-keepclassmembers class <1>$A {",
            // This test shows that we can reference the class type wildcard in the member rule.
            // 1: outer(A)
            // 2: bar
            "  <1>$A <2>(<1>$A);",
            "}")
        .inspect(
            inspector ->
                assertThat(
                    inspector.clazz(A.class).uniqueMethodWithOriginalName("bar"), isPresent()));
  }

  @Test
  public void testTypeAndReturnTypeInMember() throws Exception {
    runTest(
            "-if class **$A {",
            "  *** *(" + typeName(A.class) + ");",
            "}",
            "-keepclassmembers class <1>$A {",
            // This test shows that adding the return type wildcard does not add to the wildcard
            // refs.
            // 1: outer(A)
            // 2: bar -- expected A
            // 3: undefined -- expected bar
            // TODO(b/322114141): Update this rule if fixed in future version of PG.
            "  <1>$A <2>(<1>$A);",
            "}")
        .inspect(
            inspector ->
                assertThat(
                    inspector.clazz(A.class).uniqueMethodWithOriginalName("bar"),
                    // The rule does not match with R8.
                    shrinker.isPG() ? isPresentAndNotRenamed() : isAbsent()));
  }

  @Test
  public void testTypeAndReturnTypeInMemberAbsent() throws Exception {
    runTest(
            "-if class **$A {",
            "  *** *(" + typeName(A.class) + ");",
            "}",
            "-keepclassmembers class <1>$A {",
            "  <2> <3>(<2>);",
            "}")
        .inspect(
            inspector ->
                // The above pattern should have worked if <2> had been the return type.
                // TODO(b/322114141): Update this status if fixed in future version of PG.
                assertThat(
                    inspector.clazz(A.class).uniqueMethodWithOriginalName("bar"),
                    shrinker.isPG() ? isAbsent() : isPresentAndNotRenamed()));
  }

  public static class A {

    // Use two fields to test removal as PG won't optimize out the non-static by default.
    private final int x = 42;
    private static final int y = 42;
    private static final int xy = 42;

    public void foo(int arg) {
      System.out.println(arg + x);
    }

    public A bar(A arg) {
      return arg;
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      new A().bar(new A()).foo(args.length);
    }
  }
}
