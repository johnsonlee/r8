// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.profile.art;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IncludeTransitiveSuperClassesInArtProfileTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepAllClassesRuleWithAllowObfuscation()
        .addArtProfileForRewriting(
            ExternalArtProfile.builder().addClassRule(Reference.classFromClass(C.class)).build())
        .setMinApi(parameters)
        .compile()
        .inspectResidualArtProfile(
            (profile, inspector) ->
                profile
                    .assertContainsClassRules(
                        getFinalReference(A.class, inspector),
                        getFinalReference(B.class, inspector),
                        getFinalReference(C.class, inspector),
                        getFinalReference(I.class, inspector),
                        getFinalReference(J.class, inspector),
                        getFinalReference(K.class, inspector))
                    .assertContainsNoOtherRules());
  }

  private static ClassReference getFinalReference(Class<?> clazz, CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(clazz);
    assertThat(classSubject, isPresentAndRenamed());
    return classSubject.getFinalReference();
  }

  interface I {}

  interface J {}

  interface K extends J {}

  static class A implements I {}

  static class B extends A {}

  static class C extends B implements K {}
}
