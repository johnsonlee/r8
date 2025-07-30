// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.profile.art.ArtProfileMethodRuleInfoImpl;
import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EquivalentConstructorsWithDifferentProfileFlagsTest extends TestBase {

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
        .compile()
        .inspectResidualArtProfile(
            (profile, inspector) -> {
              ClassSubject classSubject = inspector.clazz(A.class);
              assertThat(classSubject, isPresent());

              MethodSubject initializerSubject = classSubject.init("int", "int");
              assertThat(initializerSubject, isPresent());

              profile
                  .assertContainsMethodRule(initializerSubject)
                  .inspectMethodRule(
                      initializerSubject,
                      methodRuleInspector ->
                          methodRuleInspector
                              .assertIsHot()
                              .assertIsPostStartup()
                              .assertIsStartup());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("f=0", "f=0", "f=0");
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println(new A(args.length));
      System.out.println(new B(args.length));
      System.out.println(new C(args.length));
    }
  }

  static class A {

    int f;

    @NeverInline
    A(int f) {
      this.f = f;
    }

    @Override
    public String toString() {
      return "f=" + f;
    }
  }

  static class B {

    int f;

    @NeverInline
    B(int f) {
      this.f = f;
    }

    @Override
    public String toString() {
      return "f=" + f;
    }
  }

  static class C {

    int f;

    @NeverInline
    C(int f) {
      this.f = f;
    }

    @Override
    public String toString() {
      return "f=" + f;
    }
  }
}
