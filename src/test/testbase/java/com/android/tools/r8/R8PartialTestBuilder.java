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
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class R8PartialTestBuilder
    extends R8TestBuilder<R8PartialTestCompileResult, R8TestRunResult, R8PartialTestBuilder> {

  private R8PartialConfiguration r8PartialConfiguration =
      R8PartialConfiguration.defaultConfiguration();

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

  public static class R8PartialConfiguration implements Predicate<String> {
    private static final R8PartialConfiguration defaultConfiguration =
        new R8PartialConfiguration(ImmutableList.of(), ImmutableList.of());
    private final List<Predicate<String>> includePredicates;
    private final List<Predicate<String>> excludePredicates;

    public R8PartialConfiguration(
        List<Predicate<String>> includePredicates, List<Predicate<String>> excludePredicates) {
      this.includePredicates = includePredicates;
      this.excludePredicates = excludePredicates;
    }

    private static R8PartialConfiguration defaultConfiguration() {
      return defaultConfiguration;
    }

    public static Builder builder() {
      return new Builder();
    }

    public boolean test(String name) {
      for (Predicate<String> isR8ClassPredicate : includePredicates) {
        if (isR8ClassPredicate.test(name)) {
          for (Predicate<String> isD8ClassPredicate : excludePredicates) {
            if (isD8ClassPredicate.test(name)) {
              return false;
            }
          }
          return true;
        }
      }
      return false;
    }

    public static class Builder {
      private final List<Predicate<String>> includePredicates = new ArrayList<>();
      private final List<Predicate<String>> excludePredicates = new ArrayList<>();

      public R8PartialConfiguration build() {
        return new R8PartialConfiguration(includePredicates, excludePredicates);
      }

      public Builder includeAll() {
        includePredicates.add(Predicates.alwaysTrue());
        return this;
      }

      public Builder includeClasses(Class<?>... classes) {
        return includeClasses(Arrays.asList(classes));
      }

      public Builder includeClasses(Collection<Class<?>> classes) {
        Collection<String> typeNames =
            classes.stream().map(Class::getTypeName).collect(Collectors.toList());
        includePredicates.add(typeNames::contains);
        return this;
      }

      public Builder include(Predicate<String> include) {
        includePredicates.add(include);
        return this;
      }

      public Builder excludeClasses(Class<?>... classes) {
        return excludeClasses(Arrays.asList(classes));
      }

      public Builder excludeClasses(Collection<Class<?>> classes) {
        Collection<String> typeNames =
            classes.stream().map(Class::getTypeName).collect(Collectors.toList());
        excludePredicates.add(typeNames::contains);
        return this;
      }

      public Builder exclude(Predicate<String> exclude) {
        excludePredicates.add(exclude);
        return this;
      }
    }
  }

  public R8PartialTestBuilder setR8PartialConfigurationPredicate(Predicate<String> include) {
    assert r8PartialConfiguration == R8PartialConfiguration.defaultConfiguration()
        : "Overwriting configuration...?";
    r8PartialConfiguration = R8PartialConfiguration.builder().include(include).build();
    return self();
  }

  public R8PartialTestBuilder setR8PartialConfiguration(R8PartialConfiguration configuration) {
    assert r8PartialConfiguration == R8PartialConfiguration.defaultConfiguration()
        : "Overwriting configuration...?";
    r8PartialConfiguration = configuration;
    return self();
  }

  public R8PartialTestBuilder setR8PartialConfiguration(
      Function<R8PartialConfiguration.Builder, R8PartialConfiguration> fn) {
    assert r8PartialConfiguration == R8PartialConfiguration.defaultConfiguration()
        : "Overwriting configuration...?";
    r8PartialConfiguration = fn.apply(R8PartialConfiguration.builder());
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
    Consumer<InternalOptions> configureR8PartialCompilation =
        options -> {
          options.r8PartialCompilationOptions.enabled = true;
          options.r8PartialCompilationOptions.isR8 = r8PartialConfiguration;
        };
    ToolHelper.runAndBenchmarkR8WithoutResult(
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
        buildMetadata != null ? buildMetadata.get() : null);
  }
}
