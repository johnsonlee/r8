// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.features;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static com.android.tools.r8.utils.codeinspector.AssertUtils.assertFailsCompilationIf;
import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoAccessModification;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.features.diagnostic.IllegalAccessWithIsolatedFeatureSplitsDiagnostic;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InliningLeadsToPackagePrivateIsolatedSplitCrossBoundaryReferenceTest extends TestBase {

  @Parameter(0)
  public boolean enableIsolatedSplits;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, isolated splits: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withDexRuntimesAndAllApiLevels().build());
  }

  @Test
  public void test() throws Exception {
    Box<CodeInspector> baseInspectorBox = new Box<>();
    assertFailsCompilationIf(
        enableIsolatedSplits,
        () ->
            testForR8(parameters.getBackend())
                .addProgramClasses(Base.class)
                .addFeatureSplit(Feature.class)
                .addKeepClassAndMembersRules(Feature.class)
                .enableInliningAnnotations()
                .enableIsolatedSplits(enableIsolatedSplits)
                .enableNoAccessModificationAnnotationsForMembers()
                .setMinApi(parameters)
                .compileWithExpectedDiagnostics(this::inspectDiagnostics)
                .inspect(
                    baseInspectorBox::set,
                    featureInspector -> {
                      CodeInspector baseInspector = baseInspectorBox.get();
                      ClassSubject baseClassSubject = baseInspector.clazz(Base.class);
                      assertThat(baseClassSubject, isPresent());

                      // TODO(b/300247439): Should not allow inlining of callNonPublicMethod().
                      MethodSubject callNonPublicMethodSubject =
                          baseClassSubject.uniqueMethodWithOriginalName("callNonPublicMethod");
                      assertThat(callNonPublicMethodSubject, isAbsent());

                      MethodSubject nonPublicMethodSubject =
                          baseClassSubject.uniqueMethodWithOriginalName("nonPublicMethod");
                      assertThat(nonPublicMethodSubject, isPresent());

                      ClassSubject featureClassSubject = featureInspector.clazz(Feature.class);
                      assertThat(featureClassSubject, isPresent());

                      MethodSubject featureMethodSubject =
                          featureClassSubject.uniqueMethodWithOriginalName("test");
                      assertThat(featureMethodSubject, isPresent());
                      assertThat(featureMethodSubject, invokesMethod(nonPublicMethodSubject));
                    }));
  }

  // TODO(b/300247439): Should not report any diagnostics.
  private void inspectDiagnostics(TestDiagnosticMessages diagnostics) {
    if (enableIsolatedSplits) {
      diagnostics.assertErrorsMatch(
          diagnosticType(IllegalAccessWithIsolatedFeatureSplitsDiagnostic.class));
    } else {
      diagnostics.assertNoMessages();
    }
  }

  public static class Base {

    public static void callNonPublicMethod() {
      nonPublicMethod();
    }

    @NoAccessModification
    @NeverInline
    static void nonPublicMethod() {
      System.out.println("Hello, world!");
    }
  }

  public static class Feature {

    public static void test() {
      Base.callNonPublicMethod();
    }
  }
}
