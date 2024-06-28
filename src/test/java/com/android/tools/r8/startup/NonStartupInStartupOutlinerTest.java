// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.startup;

import static com.android.tools.r8.synthesis.SyntheticItemsTestUtils.syntheticNonStartupInStartupOutlineClass;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoAccessModification;
import com.android.tools.r8.NoMethodStaticizing;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.startup.profile.ExternalStartupClass;
import com.android.tools.r8.startup.profile.ExternalStartupItem;
import com.android.tools.r8.startup.profile.ExternalStartupMethod;
import com.android.tools.r8.startup.utils.StartupTestingUtils;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.Lists;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NonStartupInStartupOutlinerTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntimes()
        .withApiLevelsStartingAtIncluding(apiLevelWithNativeMultiDexSupport())
        .build();
  }

  @Test
  public void test() throws Exception {
    List<ExternalStartupItem> startupProfile =
        Lists.newArrayList(
            ExternalStartupClass.builder()
                .setClassReference(Reference.classFromClass(StartupMain.class))
                .build(),
            ExternalStartupMethod.builder()
                .setMethodReference(MethodReferenceUtils.mainMethod(StartupMain.class))
                .build());

    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addInnerClasses(getClass())
            .addKeepMainRules(StartupMain.class, NonStartupMain.class)
            .addKeepRules(
                "-keepclassmembers class " + StartupMain.class.getTypeName() + " {",
                "  void outlinePinnedInstance();",
                "  void outlinePinnedStatic();",
                "}")
            .addOptionsModification(options -> options.getStartupOptions().setEnableOutlining(true))
            .apply(StartupTestingUtils.addStartupProfile(startupProfile))
            .allowDiagnosticInfoMessages()
            .enableInliningAnnotations()
            .enableNeverClassInliningAnnotations()
            .enableNoAccessModificationAnnotationsForMembers()
            .enableNoMethodStaticizingAnnotations()
            // To allow inspecting the individual outline classes.
            .noHorizontalClassMergingOfSynthetics()
            .setMinApi(parameters)
            .compile()
            .inspectMultiDex(this::inspectPrimaryDex, this::inspectSecondaryDex);

    compileResult
        .run(parameters.getRuntime(), StartupMain.class)
        .assertSuccessWithOutputLines("main");

    compileResult
        .run(parameters.getRuntime(), NonStartupMain.class)
        .assertSuccessWithOutputLines(
            "movePrivate", "moveStatic", "outlinePinnedInstance", "outlinePinnedStatic");
  }

  private void inspectPrimaryDex(CodeInspector inspector) {
    assertEquals(1, inspector.allClasses().size());

    ClassSubject startupMainClassSubject = inspector.clazz(StartupMain.class);
    assertThat(startupMainClassSubject, isPresent());
    assertEquals(
        parameters.canInitNewInstanceUsingSuperclassConstructor() ? 4 : 3,
        startupMainClassSubject.allMethods().size());

    assertThat(startupMainClassSubject.mainMethod(), isPresent());
    assertThat(startupMainClassSubject.init(), isAbsent());
    assertThat(startupMainClassSubject.uniqueMethodWithOriginalName("movePrivate"), isAbsent());
    assertThat(
        startupMainClassSubject.uniqueMethodWithOriginalName("movePrivateAccessor"), isPresent());
    assertThat(startupMainClassSubject.uniqueMethodWithOriginalName("moveStatic"), isAbsent());
    assertThat(
        startupMainClassSubject.uniqueMethodWithOriginalName("outlinePinnedInstance"), isPresent());
    assertThat(
        startupMainClassSubject.uniqueMethodWithOriginalName("outlinePinnedStatic"), isPresent());
  }

  private void inspectSecondaryDex(CodeInspector inspector) {
    assertThat(inspector.clazz(NonStartupMain.class), isPresent());

    ClassSubject movePrivateOutline =
        inspector.clazz(syntheticNonStartupInStartupOutlineClass(StartupMain.class, 0));
    assertThat(movePrivateOutline, isPresent());
    assertTrue(
        movePrivateOutline
            .uniqueMethod()
            .streamInstructions()
            .anyMatch(i -> i.isConstString("movePrivate")));

    ClassSubject outlinePinnedStaticOutline =
        inspector.clazz(syntheticNonStartupInStartupOutlineClass(StartupMain.class, 1));
    assertThat(
        inspector.clazz(syntheticNonStartupInStartupOutlineClass(StartupMain.class, 1)),
        isPresent());
    assertTrue(
        outlinePinnedStaticOutline
            .uniqueMethod()
            .streamInstructions()
            .anyMatch(i -> i.isConstString("outlinePinnedStatic")));

    ClassSubject outlinePinnedInstanceOutline =
        inspector.clazz(syntheticNonStartupInStartupOutlineClass(StartupMain.class, 2));
    assertThat(outlinePinnedInstanceOutline, isPresent());
    assertTrue(
        outlinePinnedInstanceOutline
            .uniqueMethod()
            .streamInstructions()
            .anyMatch(i -> i.isConstString("outlinePinnedInstance")));

    ClassSubject moveStaticOutline =
        inspector.clazz(syntheticNonStartupInStartupOutlineClass(StartupMain.class, 3));
    assertThat(moveStaticOutline, isPresent());
    assertTrue(
        moveStaticOutline
            .uniqueMethod()
            .streamInstructions()
            .anyMatch(i -> i.isConstString("moveStatic")));

    assertThat(
        inspector.clazz(syntheticNonStartupInStartupOutlineClass(StartupMain.class, 4)),
        isAbsent());
  }

  @NeverClassInline
  static class StartupMain {

    public static void main(String[] args) {
      System.out.println("main");
    }

    // Moved to synthetic non-startup class.
    @NeverInline
    @NoAccessModification
    @NoMethodStaticizing
    private void movePrivate() {
      System.out.println("movePrivate");
    }

    @NeverInline
    @NoMethodStaticizing
    void movePrivateAccessor() {
      movePrivate();
    }

    // Moved to synthetic non-startup class.
    @NeverInline
    static void moveStatic() {
      System.out.println("moveStatic");
    }

    void outlinePinnedInstance() {
      System.out.println("outlinePinnedInstance");
    }

    static void outlinePinnedStatic() {
      System.out.println("outlinePinnedStatic");
    }
  }

  static class NonStartupMain {

    public static void main(String[] args) {
      new StartupMain().movePrivateAccessor();
      StartupMain.moveStatic();
      new StartupMain().outlinePinnedInstance();
      StartupMain.outlinePinnedStatic();
    }
  }
}
