// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.profile.art.ArtProfileMethodRuleInfoImpl;
import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class UndoConstructorInliningProfileFlagsTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesAndAllApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    assumeTrue(parameters.canInitNewInstanceUsingSuperclassConstructor());
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepClassAndMembersRules(Main.class)
        .addArtProfileForRewriting(
            ExternalArtProfile.builder()
                .addMethodRule(
                    Reference.methodFromMethod(Main.class.getDeclaredMethod("hot")),
                    ArtProfileMethodRuleInfoImpl.builder().setIsHot().build())
                .addMethodRule(
                    Reference.methodFromMethod(Main.class.getDeclaredMethod("postStartup")),
                    ArtProfileMethodRuleInfoImpl.builder().setIsPostStartup().build())
                .addMethodRule(
                    Reference.methodFromMethod(Main.class.getDeclaredMethod("startup")),
                    ArtProfileMethodRuleInfoImpl.builder().setIsStartup().build())
                .build())
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector.assertIsCompleteMergeGroup(A.class, B.class).assertNoOtherClassesMerged())
        .addOptionsModification(options -> options.getSingleCallerInlinerOptions().setEnable(false))
        .compile()
        .inspectResidualArtProfile(
            (profile, inspector) -> {
              ClassSubject classSubject = inspector.clazz(A.class);
              assertThat(classSubject, isPresent());

              MethodSubject initializerSubject = classSubject.init("int");
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
            });
  }

  static class Main {

    static void hot() {
      System.out.println(new A());
      System.out.println(new B());
    }

    static void postStartup() {
      System.out.println(new A());
      System.out.println(new B());
    }

    static void startup() {
      System.out.println(new A());
      System.out.println(new B());
    }
  }

  // Needed to ensure that all merge classes are in the same SCC.
  static class Base {}

  static class A extends Base {

    A() {}

    @Override
    public String toString() {
      return "A";
    }
  }

  static class B extends Base {

    B() {}

    @Override
    public String toString() {
      return "B";
    }
  }
}
