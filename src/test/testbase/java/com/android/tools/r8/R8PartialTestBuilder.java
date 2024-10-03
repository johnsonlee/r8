// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
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
import com.android.tools.r8.utils.R8PartialCompilationConfiguration;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class R8PartialTestBuilder
    extends R8TestBuilder<R8PartialTestCompileResult, R8TestRunResult, R8PartialTestBuilder> {

  private R8PartialCompilationConfiguration r8PartialConfiguration =
      R8PartialCompilationConfiguration.disabledConfiguration();

  private R8PartialTestBuilder(TestState state, Builder builder, Backend backend) {
    super(state, builder, backend);
  }

  public static R8PartialTestBuilder create(TestState state, Backend backend) {
    Builder builder = R8Command.builder(state.getDiagnosticsHandler());
    return new R8PartialTestBuilder(state, builder, backend);
  }

  public static R8PartialTestBuilder create(
      TestState state, AndroidApp.Builder appBuilder, Backend backend) {
    return new R8PartialTestBuilder(state, R8Command.builder(appBuilder.build()), backend);
  }

  @Override
  public boolean isR8TestBuilder() {
    return true;
  }

  @Override
  R8PartialTestBuilder self() {
    return this;
  }

  public R8PartialTestBuilder setR8PartialConfigurationJavaTypePredicate(
      Predicate<String> include) {
    assert r8PartialConfiguration.equals(R8PartialCompilationConfiguration.disabledConfiguration())
        : "Overwriting configuration...?";
    r8PartialConfiguration =
        R8PartialCompilationConfiguration.builder().includeJavaType(include).build();
    return self();
  }

  public R8PartialTestBuilder setR8PartialConfiguration(
      R8PartialCompilationConfiguration configuration) {
    assert r8PartialConfiguration.equals(R8PartialCompilationConfiguration.disabledConfiguration())
        : "Overwriting configuration...?";
    r8PartialConfiguration = configuration;
    return self();
  }

  public R8PartialTestBuilder setR8PartialConfiguration(
      Consumer<R8PartialCompilationConfiguration.Builder> consumer) {
    assert r8PartialConfiguration.equals(R8PartialCompilationConfiguration.disabledConfiguration())
        : "Overwriting configuration...?";
    R8PartialCompilationConfiguration.Builder builder = R8PartialCompilationConfiguration.builder();
    consumer.accept(builder);
    r8PartialConfiguration = builder.build();
    return self();
  }

  @Override
  R8PartialTestCompileResult internalCompileR8(
      Builder builder,
      Consumer<InternalOptions> optionsConsumer,
      Supplier<AndroidApp> app,
      BenchmarkResults benchmarkResults,
      StringBuilder pgConfOutput,
      Box<List<ProguardConfigurationRule>> syntheticProguardRulesConsumer,
      StringBuilder proguardMapBuilder)
      throws CompilationFailedException {
    Box<AndroidApp> r8InputAppBox = new Box<>();
    Box<AndroidApp> d8InputAppBox = new Box<>();
    Box<AndroidApp> r8OutputAppBox = new Box<>();
    Box<AndroidApp> d8OutputAppBox = new Box<>();
    Consumer<InternalOptions> configureR8PartialCompilation =
        options -> {
          options.partialCompilationConfiguration = r8PartialConfiguration;
          options.partialCompilationConfiguration.r8InputAppConsumer = r8InputAppBox::set;
          options.partialCompilationConfiguration.d8InputAppConsumer = d8InputAppBox::set;
          options.partialCompilationConfiguration.r8OutputAppConsumer = r8OutputAppBox::set;
          options.partialCompilationConfiguration.d8OutputAppConsumer = d8OutputAppBox::set;
        };
    ToolHelper.runAndBenchmarkR8PartialWithoutResult(
        builder, configureR8PartialCompilation.andThen(optionsConsumer), benchmarkResults);
    return new R8PartialTestCompileResult(
        getState(),
        getOutputMode(),
        libraryDesugaringTestConfiguration,
        app.get(),
        pgConfOutput.toString(),
        syntheticProguardRulesConsumer.get(),
        proguardMapBuilder.toString(),
        graphConsumer,
        getMinApiLevel(),
        features,
        residualArtProfiles,
        resourceShrinkerOutput,
        resourceShrinkerOutputForFeatures,
        buildMetadata != null ? buildMetadata.get() : null,
        r8InputAppBox.get(),
        d8InputAppBox.get(),
        r8OutputAppBox.get(),
        d8OutputAppBox.get());
  }
}
