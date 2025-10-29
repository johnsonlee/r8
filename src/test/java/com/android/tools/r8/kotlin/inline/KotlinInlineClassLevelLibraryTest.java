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
public class KotlinInlineClassLevelLibraryTest extends KotlinInlineTestBase {

  public KotlinInlineClassLevelLibraryTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters, "classlevel");
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
    if (kotlinParameters.isNewerThan(KotlinCompilerVersion.KOTLINC_1_4_20)) {
      assertThat(result.stderr, containsString("Exception while generating code for:"));
      assertThat(
          result.stderr,
          containsString(
              "FUN name:main visibility:public modality:FINAL <> () returnType:kotlin.Unit"));
    } else {
      assertThat(
          result.stderr,
          containsString("Backend Internal error: Exception during file facade code generation"));
    }
  }
}
