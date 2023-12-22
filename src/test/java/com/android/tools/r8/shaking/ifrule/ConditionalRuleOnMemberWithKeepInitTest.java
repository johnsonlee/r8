// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.ifrule;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ConditionalRuleOnMemberWithKeepInitTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultDexRuntime().build();
  }

  private TestParameters parameters;

  public ConditionalRuleOnMemberWithKeepInitTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private void testKeepRule(String keepRule, List<Class<?>> absent, List<Class<?>> present)
      throws IOException, ExecutionException, CompilationFailedException {
    testForR8(parameters.getBackend())
        .addProgramClasses(A.class, TestClass.class)
        .addKeepRules(keepRule)
        .addKeepMainRule(TestClass.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("bar")
        .inspect(
            inspector -> {
              absent.forEach(clazz -> assertThat(inspector.clazz(clazz), isAbsent()));
              present.forEach(clazz -> assertThat(inspector.clazz(clazz), isPresent()));
            });
  }

  @Test
  public void testJustStarConditionalKeepClassMembers() throws Exception {
    String keepRule = "-if class * -keepclasseswithmembers class <1> { <init>(); }";
    // TODO(b/316100042) We should keep A here
    testKeepRule(keepRule, ImmutableList.of(A.class), ImmutableList.of(TestClass.class));
  }

  @Test
  public void testJustStarConditionalKeepClass() throws Exception {
    String keepRule = "-if class * -keep class <1> { <init>(); }";
    // TODO(b/316100042) We should keep A here
    testKeepRule(keepRule, ImmutableList.of(A.class), ImmutableList.of(TestClass.class));
  }

  @Test
  public void testStarWithMethodConditionalKeepClassMembers() throws Exception {
    String keepRule = "-if class * { bar(); } -keepclasseswithmembers class <1> { <init>(); }";
    testKeepRule(keepRule, ImmutableList.of(), ImmutableList.of(TestClass.class, A.class));
  }

  @Test
  public void testStarWithMethodConditionalKeepClass() throws Exception {
    String keepRule = "-if class * { bar(); } -keep class <1> { <init>(); }";
    testKeepRule(keepRule, ImmutableList.of(), ImmutableList.of(TestClass.class, A.class));
  }

  static class A {
    public static void bar() {
      System.out.println("bar");
    }
  }

  static class TestClass {
    public static void main(String[] args) {
      A.bar();
    }
  }
}
