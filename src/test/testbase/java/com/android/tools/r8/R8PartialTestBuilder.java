// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.R8Command.Builder;
import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.benchmarks.BenchmarkResults;
import com.android.tools.r8.dump.CompilerDump;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.partial.R8PartialCompilationConfiguration;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.InternalOptions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class R8PartialTestBuilder
    extends R8TestBuilder<R8PartialTestCompileResult, R8TestRunResult, R8PartialTestBuilder> {

  private final ArrayList<Class<?>> includedClasses = new ArrayList<>();
  private final ArrayList<Class<?>> excludedClasses = new ArrayList<>();
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
    return false;
  }

  @Override
  public R8TestBuilder<?, ?, ?> asR8TestBuilder() {
    return null;
  }

  @Override
  public boolean isR8PartialTestBuilder() {
    return true;
  }

  @Override
  public R8PartialTestBuilder asR8PartialTestBuilder() {
    return this;
  }

  @Override
  R8PartialTestBuilder self() {
    return this;
  }

  R8PartialCompilationConfiguration getR8PartialConfiguration() {
    return r8PartialConfiguration;
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

  public R8PartialTestBuilder clearR8PartialConfiguration() {
    r8PartialConfiguration = R8PartialCompilationConfiguration.disabledConfiguration();
    return this;
  }

  public R8PartialTestBuilder addR8IncludedClasses(Class<?>... classes) {
    return addR8IncludedClasses(true, classes);
  }

  public R8PartialTestBuilder addR8IncludedClasses(
      boolean addAsProgramClasses, Class<?>... classes) {
    assert r8PartialConfiguration.equals(R8PartialCompilationConfiguration.disabledConfiguration())
        : "Overwriting configuration...?";
    Collections.addAll(includedClasses, classes);
    if (addAsProgramClasses) {
      addProgramClasses(classes);
    }
    return self();
  }

  public R8PartialTestBuilder addR8ExcludedClasses(Class<?>... classes) {
    return addR8ExcludedClasses(true, classes);
  }

  public R8PartialTestBuilder addR8ExcludedClasses(
      boolean addAsProgramClasses, Class<?>... classes) {
    assert r8PartialConfiguration.equals(R8PartialCompilationConfiguration.disabledConfiguration())
        : "Overwriting configuration...?";
    Collections.addAll(excludedClasses, classes);
    if (addAsProgramClasses) {
      addProgramClasses(classes);
    }
    return self();
  }

  private R8PartialCompilationConfiguration getPartialConfiguration() {
    if (r8PartialConfiguration != R8PartialCompilationConfiguration.disabledConfiguration()) {
      assert excludedClasses.isEmpty() && includedClasses.isEmpty();
      return r8PartialConfiguration;
    }
    R8PartialCompilationConfiguration.Builder partialBuilder =
        R8PartialCompilationConfiguration.builder();
    partialBuilder.includeClasses(includedClasses);
    partialBuilder.excludeClasses(excludedClasses);
    return partialBuilder.build();
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
    builder.setPartialCompilationConfiguration(getPartialConfiguration());
    ToolHelper.runAndBenchmarkR8PartialWithoutResult(builder, optionsConsumer, benchmarkResults);
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
        resourceShrinkerLogConsumer,
        buildMetadata != null ? buildMetadata.get() : null);
  }

  @Override
  public R8PartialTestBuilder addOptionsModification(Consumer<InternalOptions> optionsConsumer) {
    throw new Unreachable(
        "Unexpected use of R8PartialTestBuilder#addOptionsModification. "
            + "Did you mean addD8PartialOptionsModification or addR8PartialOptionsModification?");
  }

  public R8PartialTestBuilder addR8PartialOptionsModification(Consumer<InternalOptions> consumer) {
    return super.addOptionsModification(consumer);
  }

  public R8PartialTestBuilder addR8PartialD8OptionsModification(
      Consumer<InternalOptions> consumer) {
    return super.addOptionsModification(
        options ->
            options.partialCompilationConfiguration.d8DexOptionsConsumer =
                options.partialCompilationConfiguration.d8DexOptionsConsumer.andThen(consumer));
  }

  public R8PartialTestBuilder addR8PartialR8OptionsModification(
      Consumer<InternalOptions> consumer) {
    return super.addOptionsModification(
        options ->
            options.partialCompilationConfiguration.r8OptionsConsumer =
                options.partialCompilationConfiguration.r8OptionsConsumer.andThen(consumer));
  }

  public R8PartialTestBuilder addGlobalOptionsModification(Consumer<InternalOptions> consumer) {
    return addR8PartialD8OptionsModification(consumer).addR8PartialR8OptionsModification(consumer);
  }

  @Override
  public R8PartialTestBuilder allowUnnecessaryDontWarnWildcards() {
    return addR8PartialR8OptionsModification(
        options -> options.getTestingOptions().allowUnnecessaryDontWarnWildcards = true);
  }

  @Override
  public R8PartialTestBuilder allowUnusedDontWarnPatterns() {
    return addR8PartialR8OptionsModification(
        options -> options.getTestingOptions().allowUnusedDontWarnRules = true);
  }

  @Override
  public R8PartialTestBuilder applyCompilerDump(CompilerDump dump) throws IOException {
    List<String> includePatterns = dump.getR8PartialIncludePatterns();
    if (includePatterns != null) {
      List<String> excludePatterns =
          dump.getR8PartialExcludePatternsOrDefault(Collections.emptyList());
      setR8PartialConfiguration(
          configuration -> {
            includePatterns.forEach(configuration::addJavaTypeIncludePattern);
            excludePatterns.forEach(configuration::addJavaTypeExcludePattern);
          });
    }
    return super.applyCompilerDump(dump);
  }

  @Override
  public R8PartialTestBuilder enableExperimentalKeepAnnotations(
      KeepAnnotationLibrary keepAnnotationLibrary) {
    return addR8PartialOptionsModification(
            o -> o.getTestingOptions().enableEmbeddedKeepAnnotations = true)
        .addKeepAnnoLibToClasspath(keepAnnotationLibrary);
  }

  @Override
  public R8PartialTestBuilder setPartialCompilationSeed(TestParameters parameters, long seed) {
    if (parameters.getPartialCompilationTestParameters().isRandom()) {
      r8PartialConfiguration =
          R8PartialCompilationConfiguration.builder().randomizeForTesting(seed).build();
    }
    return this;
  }
}
