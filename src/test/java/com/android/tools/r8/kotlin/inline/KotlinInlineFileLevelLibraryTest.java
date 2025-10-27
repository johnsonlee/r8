// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.inline;

import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.StringUtils;
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
    return StringUtils.lines("Hello, world!", "Hello again, world!", "Hello, default");
  }

  @Override
  protected String getLibraryClass() {
    return PKG + "." + subPackage + ".lib.LibKt";
  }

  @Override
  protected void configure(R8FullTestBuilder builder) {
    builder.addKeepRules(ImmutableList.of("-keep public class * { public <methods>; }"));
  }
}
