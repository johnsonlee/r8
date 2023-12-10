// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.startup.optimization;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsentIf;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.errors.StartupClassesNonStartupFractionDiagnostic;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.startup.profile.ExternalStartupClass;
import com.android.tools.r8.startup.profile.ExternalStartupItem;
import com.android.tools.r8.startup.profile.ExternalStartupMethod;
import com.android.tools.r8.startup.utils.StartupTestingUtils;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Tests the option to provide a startup profile for guiding optimizations without impacting the DEX
 * layout.
 */
@RunWith(Parameterized.class)
public class NoStartupBoundaryOptimizationsWithoutStartupLayoutOptimizationTest extends TestBase {

  @Parameter(0)
  public boolean enableStartupProfile;

  @Parameter(1)
  public boolean enableStartupLayoutOptimization;

  @Parameter(2)
  public TestParameters parameters;

  @Parameters(name = "{2}, profile: {0}, layout: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        BooleanUtils.values(),
        getTestParameters().withDexRuntimesAndAllApiLevels().build());
  }

  @Test
  public void test() throws Exception {
    // Startup profile requires native multi dex.
    assumeTrue(!enableStartupProfile || parameters.canUseNativeMultidex());
    // Startup layout optimization is meaningless without startup profile.
    assumeTrue(enableStartupProfile || !enableStartupLayoutOptimization);
    List<ExternalStartupItem> startupProfile =
        ImmutableList.of(
            ExternalStartupClass.builder()
                .setClassReference(Reference.classFromClass(Main.class))
                .build(),
            ExternalStartupMethod.builder()
                .setMethodReference(MethodReferenceUtils.mainMethod(Main.class))
                .build());
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .allowDiagnosticMessages()
        .applyIf(
            enableStartupProfile,
            testBuilder ->
                testBuilder
                    .apply(StartupTestingUtils.addStartupProfile(startupProfile))
                    .enableStartupLayoutOptimization(enableStartupLayoutOptimization))
        .setMinApi(parameters)
        .compileWithExpectedDiagnostics(this::inspectDiagnostics)
        .applyIf(
            !enableStartupProfile || !enableStartupLayoutOptimization,
            compileResult ->
                compileResult.inspectMultiDex(
                    primaryDexInspector ->
                        inspectPrimaryDex(primaryDexInspector, compileResult.inspector())),
            compileResult ->
                compileResult.inspectMultiDex(
                    primaryDexInspector ->
                        inspectPrimaryDex(primaryDexInspector, compileResult.inspector()),
                    this::inspectSecondaryDex))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Click!");
  }

  private void inspectDiagnostics(TestDiagnosticMessages diagnostics) {
    if (enableStartupProfile && enableStartupLayoutOptimization) {
      diagnostics.assertInfosMatch(
          diagnosticType(StartupClassesNonStartupFractionDiagnostic.class));
    } else {
      diagnostics.assertNoMessages();
    }
  }

  private void inspectPrimaryDex(CodeInspector primaryDexInspector, CodeInspector appInspector) {
    if (!enableStartupProfile) {
      // Everything should be inlined into main().
      assertEquals(1, primaryDexInspector.allClasses().size());
      assertEquals(1, primaryDexInspector.clazz(Main.class).allMethods().size());
      return;
    }

    // Main.onClick() should be inlined into Main.main(), but OnClickHandler.handle() should
    // remain.
    ClassSubject onClickHandlerClassSubject = appInspector.clazz(OnClickHandler.class);
    assertThat(onClickHandlerClassSubject, isPresent());

    MethodSubject handleMethodSubject =
        onClickHandlerClassSubject.uniqueMethodWithOriginalName("handle");
    assertThat(handleMethodSubject, isPresent());

    ClassSubject mainClassSubject = primaryDexInspector.clazz(Main.class);
    assertThat(mainClassSubject, isPresent());
    assertEquals(1, mainClassSubject.allMethods().size());
    assertThat(mainClassSubject.mainMethod(), invokesMethod(handleMethodSubject));

    // OnClickHandler should not be in the primary DEX when startup layout is enabled.
    assertThat(
        primaryDexInspector.clazz(OnClickHandler.class),
        isAbsentIf(enableStartupLayoutOptimization));
  }

  private void inspectSecondaryDex(CodeInspector secondaryDexInspector) {
    assertTrue(enableStartupProfile);
    assertTrue(enableStartupLayoutOptimization);
    assertEquals(1, secondaryDexInspector.allClasses().size());

    ClassSubject onClickHandlerClassSubject = secondaryDexInspector.clazz(OnClickHandler.class);
    assertThat(onClickHandlerClassSubject, isPresent());

    MethodSubject handleMethodSubject =
        onClickHandlerClassSubject.uniqueMethodWithOriginalName("handle");
    assertThat(handleMethodSubject, isPresent());
  }

  static class Main {

    public static void main(String[] args) {
      onClick();
    }

    static void onClick() {
      OnClickHandler.handle();
    }
  }

  static class OnClickHandler {

    static void handle() {
      System.out.println("Click!");
    }
  }
}
