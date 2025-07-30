// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.profile.art.ArtProfileMethodRuleInfoImpl;
import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ConstructorMergingWithDifferentProfileFlagsTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addArtProfileForRewriting(
            ExternalArtProfile.builder()
                .addMethodRule(
                    MethodReferenceUtils.instanceConstructor(
                        Reference.classFromClass(A.class), Reference.INT),
                    ArtProfileMethodRuleInfoImpl.builder().setIsHot().build())
                .addMethodRule(
                    MethodReferenceUtils.instanceConstructor(
                        Reference.classFromClass(B.class), Reference.INT),
                    ArtProfileMethodRuleInfoImpl.builder().setIsPostStartup().build())
                .addMethodRule(
                    MethodReferenceUtils.instanceConstructor(
                        Reference.classFromClass(C.class), Reference.INT),
                    ArtProfileMethodRuleInfoImpl.builder().setIsStartup().build())
                .build())
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector
                    .assertIsCompleteMergeGroup(A.class, B.class, C.class)
                    .assertNoOtherClassesMerged())
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .compile()
        .inspectResidualArtProfile(
            (profile, inspector) -> {
              ClassSubject classSubject = inspector.clazz(A.class);
              assertThat(classSubject, isPresent());

              MethodSubject switchInitializerSubject = classSubject.init("int", "int");
              assertThat(switchInitializerSubject, isPresent());
              assertTrue(
                  switchInitializerSubject
                      .streamInstructions()
                      .anyMatch(InstructionSubject::isSwitch));

              profile
                  .assertContainsMethodRule(switchInitializerSubject)
                  .inspectMethodRule(
                      switchInitializerSubject,
                      methodRuleInspector ->
                          methodRuleInspector
                              .assertIsHot()
                              .assertIsPostStartup()
                              .assertIsStartup());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A.f=0", "B.f=0", "C.f=0");
  }

  static class Main {

    public static void main(String[] args) {
      new A(args.length);
      new B(args.length);
      new C(args.length);
    }
  }

  @NeverClassInline
  static class A {

    @NeverInline
    A(int f) {
      System.out.println("A.f=" + f);
    }
  }

  @NeverClassInline
  static class B {

    @NeverInline
    B(int f) {
      System.out.println("B.f=" + f);
    }
  }

  @NeverClassInline
  static class C {

    @NeverInline
    C(int f) {
      System.out.println("C.f=" + f);
    }
  }
}
