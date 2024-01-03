// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.keepanno.annotations.KeepOption;
import com.android.tools.r8.keepanno.annotations.KeepTarget;
import com.android.tools.r8.keepanno.annotations.StringPattern;
import com.android.tools.r8.keepanno.annotations.UsesReflection;
import com.android.tools.r8.keepanno.asm.KeepEdgeReader;
import com.android.tools.r8.keepanno.ast.KeepAnnotationParserException;
import com.android.tools.r8.keepanno.ast.KeepDeclaration;
import com.android.tools.r8.keepanno.keeprules.KeepRuleExtractor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KeepInvalidTargetTest extends TestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public KeepInvalidTargetTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  private static List<String> extractRuleForClass(Class<?> clazz) throws IOException {
    List<KeepDeclaration> keepEdges =
        KeepEdgeReader.readKeepEdges(ToolHelper.getClassAsBytes(clazz));
    List<String> rules = new ArrayList<>();
    KeepRuleExtractor extractor = new KeepRuleExtractor(rules::add);
    keepEdges.forEach(extractor::extract);
    return rules;
  }

  private void assertThrowsWith(ThrowingRunnable fn, Matcher<String> matcher) {
    try {
      fn.run();
    } catch (KeepAnnotationParserException e) {
      assertThat(e.getMessage(), matcher);
      return;
    } catch (Throwable e) {
      fail("Expected run to fail with KeepEdgeException, but failed with: " + e);
    }
    fail("Expected run to fail");
  }

  @Test
  public void testInvalidClassDecl() {
    assertThrowsWith(
        () -> extractRuleForClass(MultipleClassDeclarations.class),
        allOf(
            containsString("Multiple properties"),
            containsString("className"),
            containsString("classConstant"),
            containsString("at property-group: class-name")));
  }

  static class MultipleClassDeclarations {

    @UsesReflection(@KeepTarget(className = "foo", classConstant = MultipleClassDeclarations.class))
    public static void main(String[] args) {
      System.out.println("Hello, world");
    }
  }

  @Test
  public void testInvalidClassDeclWithBinding() {
    assertThrowsWith(
        () -> extractRuleForClass(BindingAndClassDeclarations.class),
        allOf(
            containsString("class binding"),
            containsString("class patterns"),
            containsString("at property-group: class")));
  }

  static class BindingAndClassDeclarations {

    @UsesReflection({@KeepTarget(classFromBinding = "BINDING", className = "CLASS")})
    public static void main(String[] args) {
      System.out.println("Hello, world");
    }
  }

  @Test
  public void testInvalidExtendsDecl() {
    assertThrowsWith(
        () -> extractRuleForClass(MultipleExtendsDeclarations.class),
        allOf(
            containsString("Multiple properties"),
            containsString("extendsClassName"),
            containsString("extendsClassConstant"),
            containsString("at property-group: instance-of"),
            containsString("at annotation: @UsesReflection"),
            containsString("at method: void main")));
  }

  static class MultipleExtendsDeclarations {

    @UsesReflection(
        @KeepTarget(
            extendsClassName = "foo",
            extendsClassConstant = MultipleClassDeclarations.class))
    public static void main(String[] args) {
      System.out.println("Hello, world");
    }
  }

  @Test
  public void testInvalidMemberDecl() {
    assertThrowsWith(
        () -> extractRuleForClass(MultipleMemberDeclarations.class),
        allOf(
            containsString("field"),
            containsString("method"),
            containsString("at property-group: member")));
  }

  static class MultipleMemberDeclarations {

    @UsesReflection(@KeepTarget(classConstant = A.class, methodName = "foo", fieldName = "bar"))
    public static void main(String[] args) {
      System.out.println("Hello, world");
    }
  }

  @Test
  public void testInvalidOptionsDecl() {
    assertThrowsWith(
        () -> extractRuleForClass(MultipleOptionDeclarations.class),
        allOf(
            containsString("Multiple properties"),
            containsString("allow"),
            containsString("disallow"),
            containsString("at property-group: constraints")));
  }

  static class MultipleOptionDeclarations {

    @UsesReflection(
        @KeepTarget(
            classConstant = A.class,
            allow = {KeepOption.OPTIMIZATION},
            disallow = {KeepOption.SHRINKING}))
    public static void main(String[] args) {
      System.out.println("Hello, world");
    }
  }

  @Test
  public void testStringPattern() {
    assertThrowsWith(
        () -> extractRuleForClass(StringPatternWithExactAndPrefix.class),
        allOf(
            containsString("Cannot specify both"),
            containsString("exact"),
            containsString("prefix"),
            containsString("at property: methodNamePattern")));
  }

  static class StringPatternWithExactAndPrefix {

    @UsesReflection(
        @KeepTarget(
            classConstant = A.class,
            methodNamePattern = @StringPattern(exact = "foo", startsWith = "f")))
    public static void main(String[] args) {
      System.out.println("Hello, world");
    }
  }

  static class A {
    // just a target.
  }
}
