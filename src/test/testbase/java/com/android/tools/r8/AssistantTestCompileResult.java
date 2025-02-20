// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

public class AssistantTestCompileResult
    extends TestCompileResult<AssistantTestCompileResult, AssistantTestRunResult> {

  private final Path initialOutput;

  public AssistantTestCompileResult(
      Path initialOutput, TestState testState, AndroidApp androidApp, int minApi) {
    super(testState, androidApp, minApi, OutputMode.DexIndexed);
    this.initialOutput = initialOutput;
  }

  @Override
  public AssistantTestCompileResult self() {
    return this;
  }

  @Override
  public TestDiagnosticMessages getDiagnosticMessages() {
    return state.getDiagnosticsMessages();
  }

  @Override
  public Set<String> getMainDexClasses() {
    throw new Unimplemented("No support for main dex in assistant");
  }

  @Override
  public String getStdout() {
    return state.getStdout();
  }

  @Override
  public String getStderr() {
    return state.getStderr();
  }

  @Override
  protected AssistantTestRunResult createRunResult(TestRuntime runtime, ProcessResult result) {
    return new AssistantTestRunResult(app, runtime, result, state);
  }

  public <E extends Throwable> AssistantTestCompileResult inspectOriginalDex(
      ThrowingConsumer<CodeInspector, E> consumer) throws IOException, E {
    consumer.accept(new CodeInspector(initialOutput));
    return self();
  }
}
