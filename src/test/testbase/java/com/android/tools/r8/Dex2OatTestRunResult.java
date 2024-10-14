// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.AndroidApp;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.hamcrest.Matcher;

public class Dex2OatTestRunResult extends SingleTestRunResult<Dex2OatTestRunResult> {

  private final Path oat;

  public Dex2OatTestRunResult(
      AndroidApp app, Path oat, TestRuntime runtime, ProcessResult result, TestState state) {
    super(app, runtime, result, state);
    this.oat = oat;
  }

  @Override
  protected Dex2OatTestRunResult self() {
    return this;
  }

  public Dex2OatTestRunResult assertNoLockVerificationErrors() {
    assertSuccess();
    Matcher<? super String> matcher = not(containsString("failed lock verification"));
    assertThat(
        errorMessage("Run dex2oat produced lock verification errors.", matcher.toString()),
        getStdErr(),
        matcher);
    return self();
  }

  public Dex2OatTestRunResult assertNoVerificationErrors() {
    assertSuccess();
    Matcher<? super String> matcher = not(containsString("Verification error"));
    assertThat(
        errorMessage("Run dex2oat produced verification errors.", matcher.toString()),
        getStdErr(),
        matcher);
    return self();
  }

  public Dex2OatTestRunResult assertSoftVerificationErrors() {
    assertSuccess();
    Matcher<? super String> matcher = containsString("Soft verification failures");
    assertThat(
        errorMessage("Run dex2oat did not produce soft verification errors.", matcher.toString()),
        getStdErr(),
        matcher);
    return self();
  }

  public long getOatSizeOrDefault(long defaultValue) throws IOException {
    return Files.exists(oat) ? Files.size(oat) : defaultValue;
  }
}
