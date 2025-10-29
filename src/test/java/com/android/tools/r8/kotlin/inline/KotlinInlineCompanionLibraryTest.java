// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.inline;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassOrMemberSubject;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KotlinInlineCompanionLibraryTest extends KotlinInlineTestBase {

  public KotlinInlineCompanionLibraryTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters, "companion");
    this.parameters = parameters;
  }

  @Override
  protected String getExpected() {
    return StringUtils.lines("1", "2");
  }

  @Override
  protected String getLibraryClass() {
    return PKG + "." + subPackage + ".lib.A";
  }

  @Override
  protected void configure(R8FullTestBuilder builder) {
    builder
        .addKeepAttributeInnerClassesAndEnclosingMethod()
        .addKeepRules(
            ImmutableList.of(
                "-keep public class * { public static * Companion; public <methods>; }"));
  }

  @Override
  void inspect(CodeInspector inspector) {
    ClassSubject companionClass = inspector.clazz(getLibraryClass() + "$Companion");
    companionClass
        .allMethods(ClassOrMemberSubject::isPublic)
        .forEach(
            method -> {
              assertThat(method, isPresentAndNotRenamed());
              assertEquals(!method.isInstanceInitializer(), method.hasLocalVariableTable());
            });
  }
}
