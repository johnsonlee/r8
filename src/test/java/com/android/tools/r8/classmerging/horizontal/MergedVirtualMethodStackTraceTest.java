// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.naming.retrace.StackTrace.isSame;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoMethodStaticizing;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.naming.retrace.StackTrace;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MergedVirtualMethodStackTraceTest extends TestBase {

  private static StackTrace expectedStackTrace;

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @BeforeClass
  public static void setup() throws Exception {
    // Get the expected stack trace by running on the JVM.
    expectedStackTrace =
        testForJvm(getStaticTemp())
            .addTestClasspath()
            .run(CfRuntime.getSystemRuntime(), Main.class)
            .assertFailure()
            .map(StackTrace::extractFromJvm);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepAttributeLineNumberTable()
        .addKeepAttributeSourceFile()
        .addHorizontallyMergedClassesInspector(
            inspector -> inspector.assertMergedInto(B.class, A.class).assertNoOtherClassesMerged())
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoMethodStaticizingAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .inspectStackTrace(
            (stackTrace, codeInspector) -> {
              assertThat(codeInspector.clazz(A.class), isPresent());
              assertThat(codeInspector.clazz(B.class), isAbsent());
              assertThat(stackTrace, isSame(expectedStackTrace));
            });
  }

  @NeverClassInline
  public static class A {

    @NeverInline
    @NoMethodStaticizing
    public void foo() {
      System.out.println("foo a");
    }
  }

  @NeverClassInline
  public static class B {

    @NeverInline
    @NoMethodStaticizing
    public void foo() {
      throw new RuntimeException();
    }
  }

  public static class Main {
    public static void main(String[] args) {
      new A().foo();
      new B().foo();
    }
  }
}
