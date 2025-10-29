// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.inline;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

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
public class KotlinInlineFileLevelLibraryTest extends KotlinInlineTestBase {

  public KotlinInlineFileLevelLibraryTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters, "filelevel");
    this.parameters = parameters;
  }

  @Override
  protected String getExpected() {
    return StringUtils.lines("1", "2", "3", "4", "5", "6");
  }

  @Override
  protected String getLibraryClass() {
    return PKG + "." + subPackage + ".lib.LibKt";
  }

  @Override
  protected void configure(R8FullTestBuilder builder) {
    builder.addKeepRules(ImmutableList.of("-keep public class * { public <methods>; }"));
  }

  @Override
  void inspect(CodeInspector inspector) {
    ClassSubject libraryClass = inspector.clazz(getLibraryClass());
    libraryClass
        .allMethods(ClassOrMemberSubject::isPublic)
        .forEach(
            method -> {
              assertThat(method, isPresentAndNotRenamed());
              assertTrue(method.hasLocalVariableTable());
            });
  }
}
