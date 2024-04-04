// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.junit.Assert.assertThrows;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.errors.UnsupportedMainDexListUsageDiagnostic;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class PreventMergeMainDexListTest extends HorizontalClassMergingTestBase {
  public PreventMergeMainDexListTest(TestParameters parameters) {
    super(parameters);
  }

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDefaultDexRuntime()
        .withApiLevelsEndingAtExcluding(apiLevelWithNativeMultiDexSupport())
        .build();
  }

  // TODO(b/181858113): This test is likely obsolete once main-dex-list support is removed.
  //  Ensure the main-dex-rules variant of this test (PreventMergeMainDexTracingTest) is sufficient.
  @Test
  public void testR8() throws Exception {
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForR8(parameters.getBackend())
                .addInnerClasses(getClass())
                .addKeepClassAndMembersRules(Main.class)
                .addMainDexListClasses(A.class, Main.class)
                .addOptionsModification(options -> options.minimalMainDex = true)
                .enableNeverClassInliningAnnotations()
                .setMinApi(parameters)
                .allowDiagnosticMessages()
                .compileWithExpectedDiagnostics(
                    diagnostics ->
                        diagnostics
                            .assertOnlyErrors()
                            .assertErrorsMatch(
                                diagnosticType(UnsupportedMainDexListUsageDiagnostic.class))));
  }

  public static class Main {
    public static void main(String[] args) {
      A a = new A();
    }

    public static void otherDex() {
      B b = new B();
    }
  }

  @NeverClassInline
  public static class A {
    public A() {
      System.out.println("main dex");
    }
  }

  @NeverClassInline
  public static class B {
    public B() {
      System.out.println("not main dex");
    }
  }
}
