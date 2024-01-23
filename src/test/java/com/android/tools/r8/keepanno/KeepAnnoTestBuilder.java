// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno;

import com.android.tools.r8.ExternalR8TestBuilder;
import com.android.tools.r8.ProguardTestBuilder;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.keepanno.keeprules.KeepRuleExtractorOptions;
import java.io.IOException;
import java.util.List;
import org.junit.rules.TemporaryFolder;

public abstract class KeepAnnoTestBuilder {

  public static KeepAnnoTestBuilder forKeepAnnoTest(KeepAnnoParameters params, TemporaryFolder temp)
      throws IOException {
    switch (params.config()) {
      case REFERENCE:
        return new ReferenceBuilder(params, temp);
      case R8_NATIVE:
        return new R8NativeBuilder(params, temp);
      case R8_LEGACY:
        return new R8LegacyBuilder(params, temp);
      case PG:
        return new PGBuilder(params, temp);
      default:
        throw new IllegalStateException("Unexpected keep anno config: " + params.config());
    }
  }

  private final KeepAnnoParameters keepAnnoParams;
  private boolean printRules = false;

  private KeepAnnoTestBuilder(KeepAnnoParameters params, TemporaryFolder temp) {
    this.keepAnnoParams = params;
  }

  boolean shouldPrintRules() {
    return printRules;
  }

  public final TestParameters parameters() {
    return keepAnnoParams.parameters();
  }

  public abstract KeepAnnoTestBuilder addProgramClasses(List<Class<?>> programClasses)
      throws IOException;

  public abstract KeepAnnoTestBuilder addKeepMainRule(Class<?> mainClass);

  public abstract SingleTestRunResult<?> run(Class<?> mainClass) throws Exception;

  public KeepAnnoTestBuilder applyIfShrinker(
      ThrowableConsumer<TestShrinkerBuilder<?, ?, ?, ?, ?>> builderConsumer) {
    applyIfR8(builderConsumer);
    applyIfPG(builderConsumer::accept);
    return this;
  }

  public KeepAnnoTestBuilder applyIfR8(
      ThrowableConsumer<TestShrinkerBuilder<?, ?, ?, ?, ?>> builderConsumer) {
    return this;
  }

  public KeepAnnoTestBuilder applyIfR8Native(ThrowableConsumer<R8TestBuilder<?>> builderConsumer) {
    return this;
  }

  public KeepAnnoTestBuilder applyIfPG(ThrowableConsumer<ProguardTestBuilder> builderConsumer) {
    return this;
  }

  public final KeepAnnoTestBuilder setExcludedOuterClass(Class<?> clazz) {
    return applyIfPG(b -> b.addDontWarn(clazz));
  }

  public final KeepAnnoTestBuilder allowUnusedProguardConfigurationRules() {
    return applyIfR8Native(R8TestBuilder::allowUnusedProguardConfigurationRules);
  }

  public final KeepAnnoTestBuilder allowAccessModification() {
    applyIfShrinker(TestShrinkerBuilder::allowAccessModification);
    return this;
  }

  public KeepAnnoTestBuilder printRules() {
    printRules = true;
    return this;
  }

  private static class ReferenceBuilder extends KeepAnnoTestBuilder {

    private final TestBuilder<? extends SingleTestRunResult<?>, ?> builder;

    public ReferenceBuilder(KeepAnnoParameters params, TemporaryFolder temp) {
      super(params, temp);
      if (parameters().isCfRuntime()) {
        builder = TestBase.testForJvm(temp);
      } else {
        assert parameters().isDexRuntime();
        builder = TestBase.testForD8(temp).setMinApi(parameters());
      }
    }

    @Override
    public KeepAnnoTestBuilder addProgramClasses(List<Class<?>> programClasses) {
      builder.addProgramClasses(programClasses);
      return this;
    }

    @Override
    public KeepAnnoTestBuilder addKeepMainRule(Class<?> mainClass) {
      // Nothing to keep in JVM/D8.
      return this;
    }

    @Override
    public SingleTestRunResult<?> run(Class<?> mainClass) throws Exception {
      return builder.run(parameters().getRuntime(), mainClass);
    }
  }

  private static class R8NativeBuilder extends KeepAnnoTestBuilder {

    private final R8FullTestBuilder builder;

    public R8NativeBuilder(KeepAnnoParameters params, TemporaryFolder temp) {
      super(params, temp);
      builder =
          TestBase.testForR8(temp, parameters().getBackend())
              .enableExperimentalKeepAnnotations()
              .setMinApi(parameters());
    }

    @Override
    public KeepAnnoTestBuilder applyIfR8(
        ThrowableConsumer<TestShrinkerBuilder<?, ?, ?, ?, ?>> builderConsumer) {
      builderConsumer.acceptWithRuntimeException(builder);
      return this;
    }

    @Override
    public KeepAnnoTestBuilder applyIfR8Native(
        ThrowableConsumer<R8TestBuilder<?>> builderConsumer) {
      builderConsumer.acceptWithRuntimeException(builder);
      return this;
    }

    @Override
    public KeepAnnoTestBuilder addProgramClasses(List<Class<?>> programClasses) {
      builder.addProgramClasses(programClasses);
      return this;
    }

    @Override
    public KeepAnnoTestBuilder addKeepMainRule(Class<?> mainClass) {
      builder.addKeepMainRule(mainClass);
      return this;
    }

    @Override
    public SingleTestRunResult<?> run(Class<?> mainClass) throws Exception {
      R8TestCompileResult compileResult = builder.compile();
      if (shouldPrintRules()) {
        System.out.println(compileResult.getProguardConfiguration());
      }
      return compileResult.run(parameters().getRuntime(), mainClass);
    }
  }

  private static class R8LegacyBuilder extends KeepAnnoTestBuilder {

    private final KeepRuleExtractorOptions extractorOptions =
        KeepRuleExtractorOptions.getR8Options();
    private final ExternalR8TestBuilder builder;

    public R8LegacyBuilder(KeepAnnoParameters params, TemporaryFolder temp) throws IOException {
      super(params, temp);
      builder =
          TestBase.testForExternalR8(temp, parameters().getBackend())
              .useProvidedR8(KeepAnnoTestUtils.R8_LIB)
              .addProgramFiles(KeepAnnoTestUtils.getKeepAnnoLib(temp))
              .setMinApi(parameters());
    }

    @Override
    public KeepAnnoTestBuilder applyIfR8(
        ThrowableConsumer<TestShrinkerBuilder<?, ?, ?, ?, ?>> builderConsumer) {
      builderConsumer.acceptWithRuntimeException(builder);
      return this;
    }

    @Override
    public KeepAnnoTestBuilder addProgramClasses(List<Class<?>> programClasses) throws IOException {
      List<String> rules = KeepAnnoTestUtils.extractRules(programClasses, extractorOptions);
      builder.addProgramClasses(programClasses);
      builder.addKeepRules(rules);
      if (shouldPrintRules()) {
        rules.forEach(System.out::println);
      }
      return this;
    }

    @Override
    public KeepAnnoTestBuilder addKeepMainRule(Class<?> mainClass) {
      builder.addKeepMainRule(mainClass);
      return this;
    }

    @Override
    public SingleTestRunResult<?> run(Class<?> mainClass) throws Exception {
      return builder.run(parameters().getRuntime(), mainClass);
    }
  }

  private static class PGBuilder extends KeepAnnoTestBuilder {

    private final KeepRuleExtractorOptions extractorOptions =
        KeepRuleExtractorOptions.getPgOptions();
    private final ProguardTestBuilder builder;

    public PGBuilder(KeepAnnoParameters params, TemporaryFolder temp) throws IOException {
      super(params, temp);
      builder =
          TestBase.testForProguard(KeepAnnoTestUtils.PG_VERSION, temp)
              .addProgramFiles(KeepAnnoTestUtils.getKeepAnnoLib(temp))
              .setMinApi(parameters());
    }

    @Override
    public KeepAnnoTestBuilder applyIfPG(ThrowableConsumer<ProguardTestBuilder> builderConsumer) {
      builderConsumer.acceptWithRuntimeException(builder);
      return this;
    }

    @Override
    public KeepAnnoTestBuilder addProgramClasses(List<Class<?>> programClasses) throws IOException {
      List<String> rules = KeepAnnoTestUtils.extractRules(programClasses, extractorOptions);
      builder.addProgramClasses(programClasses);
      builder.addKeepRules(rules);
      if (shouldPrintRules()) {
        rules.forEach(System.out::println);
      }
      return this;
    }

    @Override
    public KeepAnnoTestBuilder addKeepMainRule(Class<?> mainClass) {
      builder.addKeepMainRule(mainClass);
      return this;
    }

    @Override
    public SingleTestRunResult<?> run(Class<?> mainClass) throws Exception {
      return builder.run(parameters().getRuntime(), mainClass);
    }
  }
}
