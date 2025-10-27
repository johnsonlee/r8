// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.inline;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.StringUtils;
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
    return StringUtils.lines("Hello, world!", "Hello again, world!", "Hello, default");
  }

  @Override
  protected String getLibraryClass() {
    return PKG + "." + subPackage + ".lib.A";
  }

  @Override
  protected void configure(R8FullTestBuilder builder) {
    builder.addKeepRules(ImmutableList.of("-keep public class * { public <methods>; }"));
  }

  @Override
  protected boolean kotlinCompilationFails() {
    return true;
  }

  @Override
  protected void kotlinCompilationResult(ProcessResult result) {
    assertThat(
        result.stderr,
        containsString(
            kotlinParameters
                    .getCompilerVersion()
                    .isGreaterThanOrEqualTo(KotlinCompilerVersion.KOTLINC_2_0_20)
                ? "main.kt:8:5: error: unresolved reference 'f'"
                : "main.kt:8:5: error: unresolved reference: f"));
    assertThat(
        result.stderr,
        containsString(
            kotlinParameters
                    .getCompilerVersion()
                    .isGreaterThanOrEqualTo(KotlinCompilerVersion.KOTLINC_2_0_20)
                ? "main.kt:9:5: error: unresolved reference 'f'"
                : "main.kt:9:5: error: unresolved reference: f"));
    assertThat(
        result.stderr,
        containsString(
            kotlinParameters
                    .getCompilerVersion()
                    .isGreaterThanOrEqualTo(KotlinCompilerVersion.KOTLINC_2_0_20)
                ? "main.kt:10:5: error: unresolved reference 'g'"
                : "main.kt:10:5: error: unresolved reference: g"));
  }
}
