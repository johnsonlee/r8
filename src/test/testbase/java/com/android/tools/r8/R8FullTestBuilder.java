// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.R8Command.Builder;
import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.benchmarks.BenchmarkResults;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.InternalOptions;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class R8FullTestBuilder
    extends R8TestBuilder<R8TestCompileResult, R8TestRunResult, R8FullTestBuilder> {

  private R8FullTestBuilder(TestState state, Builder builder, Backend backend) {
    super(state, builder, backend);
  }

  public static R8FullTestBuilder create(TestState state, Backend backend) {
    Builder builder = R8Command.builder(state.getDiagnosticsHandler());
    return new R8FullTestBuilder(state, builder, backend);
  }

  public static R8FullTestBuilder create(
      TestState state, AndroidApp.Builder appBuilder, Backend backend) {
    return new R8FullTestBuilder(state, R8Command.builder(appBuilder.build()), backend);
  }

  @Override
  R8FullTestBuilder self() {
    return this;
  }

  R8TestCompileResult internalCompileR8(
      Builder builder,
      Consumer<InternalOptions> optionsConsumer,
      Supplier<AndroidApp> app,
      BenchmarkResults benchmarkResults,
      StringBuilder pgConfOutput,
      Box<List<ProguardConfigurationRule>> syntheticProguardRulesConsumer,
      StringBuilder proguardMapBuilder)
      throws CompilationFailedException {
    ToolHelper.runAndBenchmarkR8WithoutResult(builder, optionsConsumer, benchmarkResults);
    return new R8TestCompileResult(
        getState(),
        getOutputMode(),
        libraryDesugaringTestConfiguration,
        app.get(),
        pgConfOutput.toString(),
        syntheticProguardRulesConsumer.get(),
        proguardMapBuilder != null ? proguardMapBuilder.toString() : null,
        graphConsumer,
        getMinApiLevel(),
        features,
        residualArtProfiles,
        resourceShrinkerOutput,
        resourceShrinkerOutputForFeatures,
        resourceShrinkerLogConsumer,
        buildMetadata != null ? buildMetadata.get() : null);
  }
}
