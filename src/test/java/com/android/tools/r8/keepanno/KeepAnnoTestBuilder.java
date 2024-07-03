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
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.keepanno.KeepAnnoParameters.KeepAnnoConfig;
import com.android.tools.r8.keepanno.asm.KeepEdgeReader;
import com.android.tools.r8.keepanno.ast.KeepDeclaration;
import com.android.tools.r8.keepanno.ast.KeepSpecVersion;
import com.android.tools.r8.keepanno.keeprules.KeepRuleExtractor;
import com.android.tools.r8.keepanno.keeprules.KeepRuleExtractorOptions;
import com.android.tools.r8.keepanno.proto.KeepSpecProtos.KeepSpec;
import com.android.tools.r8.origin.Origin;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import org.junit.rules.TemporaryFolder;

public abstract class KeepAnnoTestBuilder {

  public static KeepAnnoTestBuilder forKeepAnnoTest(KeepAnnoParameters params, TemporaryFolder temp)
      throws IOException {
    switch (params.config()) {
      case REFERENCE:
        return new ReferenceBuilder(params, temp);
      case R8_DIRECT:
      case R8_NORMALIZED:
      case R8_RULES:
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

  private KeepAnnoTestBuilder(KeepAnnoParameters params, TemporaryFolder temp) {
    this.keepAnnoParams = params;
  }

  public final TestParameters parameters() {
    return keepAnnoParams.parameters();
  }

  public KeepAnnoTestBuilder addInnerClasses(Class<?> clazz) throws IOException {
    return addProgramFiles(new ArrayList<>(ToolHelper.getClassFilesForInnerClasses(clazz)));
  }

  public abstract KeepAnnoTestBuilder addProgramFiles(List<Path> programFiles) throws IOException;

  public final KeepAnnoTestBuilder addProgramClasses(Class<?>... programClasses)
      throws IOException {
    return addProgramClasses(Arrays.asList(programClasses));
  }

  public abstract KeepAnnoTestBuilder addProgramClasses(List<Class<?>> programClasses)
      throws IOException;

  public final KeepAnnoTestBuilder addProgramClassFileData(byte[]... programClasses)
      throws IOException {
    return addProgramClassFileData(Arrays.asList(programClasses));
  }

  public abstract KeepAnnoTestBuilder addProgramClassFileData(List<byte[]> programClasses)
      throws IOException;

  public final KeepAnnoTestBuilder addKeepMainRule(Class<?> mainClass) {
    return applyIfShrinker(b -> b.addKeepMainRule(mainClass));
  }

  public final KeepAnnoTestBuilder addKeepClassRules(Class<?>... classes) {
    return applyIfShrinker(b -> b.addKeepClassRules(classes));
  }

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

  public KeepAnnoTestBuilder applyIfR8Current(ThrowableConsumer<R8TestBuilder<?>> builderConsumer) {
    return this;
  }

  public KeepAnnoTestBuilder applyIfPG(ThrowableConsumer<ProguardTestBuilder> builderConsumer) {
    return this;
  }

  public KeepAnnoTestBuilder enableNativeInterpretation() {
    return this;
  }

  public final KeepAnnoTestBuilder setExcludedOuterClass(Class<?> clazz) {
    return applyIfPG(b -> b.addDontWarn(clazz));
  }

  public KeepAnnoTestBuilder allowUnusedProguardConfigurationRules() {
    return this;
  }

  public final KeepAnnoTestBuilder allowAccessModification() {
    applyIfShrinker(TestShrinkerBuilder::allowAccessModification);
    return this;
  }

  public final KeepAnnoTestBuilder printRules() {
    return inspectOutputConfig(System.out::println);
  }

  public KeepAnnoTestBuilder inspectOutputConfig(Consumer<String> configConsumer) {
    // Default to ignore the consumer.
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
    public KeepAnnoTestBuilder addProgramFiles(List<Path> programFiles) {
      builder.addProgramFiles(programFiles);
      return this;
    }

    @Override
    public KeepAnnoTestBuilder addProgramClasses(List<Class<?>> programClasses) {
      builder.addProgramClasses(programClasses);
      return this;
    }

    @Override
    public KeepAnnoTestBuilder addProgramClassFileData(List<byte[]> programClasses)
        throws IOException {
      builder.addProgramClassFileData(programClasses);
      return this;
    }

    @Override
    public SingleTestRunResult<?> run(Class<?> mainClass) throws Exception {
      return builder.run(parameters().getRuntime(), mainClass);
    }
  }

  private static class R8NativeBuilder extends KeepAnnoTestBuilder {

    private KeepAnnoConfig config;
    private final R8FullTestBuilder builder;
    private List<Consumer<R8TestCompileResult>> compileResultConsumers = new ArrayList<>();

    private R8NativeBuilder(KeepAnnoParameters params, TemporaryFolder temp) {
      super(params, temp);
      config = params.config();
      builder =
          TestBase.testForR8(temp, parameters().getBackend())
              .enableExperimentalKeepAnnotations()
              .setMinApi(parameters());

      // TODO(b/323816623): Replace the testing flag by the API call.
      builder.getBuilder().setEnableExperimentalKeepAnnotations(false);
      builder.addOptionsModification(o -> o.testing.enableEmbeddedKeepAnnotations = isDirect());
    }

    private boolean isExtractRules() {
      return config == KeepAnnoConfig.R8_RULES;
    }

    private boolean isNormalizeEdges() {
      return config == KeepAnnoConfig.R8_NORMALIZED;
    }

    private boolean isDirect() {
      return config == KeepAnnoConfig.R8_DIRECT;
    }

    @Override
    public KeepAnnoTestBuilder allowUnusedProguardConfigurationRules() {
      if (isExtractRules()) {
        builder.allowUnusedProguardConfigurationRules();
      }
      return this;
    }

    @Override
    public KeepAnnoTestBuilder applyIfR8(
        ThrowableConsumer<TestShrinkerBuilder<?, ?, ?, ?, ?>> builderConsumer) {
      builderConsumer.acceptWithRuntimeException(builder);
      return this;
    }

    @Override
    public KeepAnnoTestBuilder applyIfR8Current(
        ThrowableConsumer<R8TestBuilder<?>> builderConsumer) {
      builderConsumer.acceptWithRuntimeException(builder);
      return this;
    }

    @Override
    public KeepAnnoTestBuilder addProgramFiles(List<Path> programFiles) throws IOException {
      for (Path programFile : programFiles) {
        extractAndAdd(Files.readAllBytes(programFile));
      }
      return this;
    }

    @Override
    public KeepAnnoTestBuilder addProgramClasses(List<Class<?>> programClasses) throws IOException {
      for (Class<?> programClass : programClasses) {
        extractAndAdd(ToolHelper.getClassAsBytes(programClass));
      }
      return this;
    }

    @Override
    public KeepAnnoTestBuilder addProgramClassFileData(List<byte[]> programClasses)
        throws IOException {
      for (byte[] programClass : programClasses) {
        extractAndAdd(programClass);
      }
      return this;
    }

    @Override
    public KeepAnnoTestBuilder inspectOutputConfig(Consumer<String> configConsumer) {
      compileResultConsumers.add(
          result -> configConsumer.accept(result.getProguardConfiguration()));
      return this;
    }

    private void extractAndAdd(byte[] classFileData) {
      builder.addProgramClassFileData(classFileData);
      if (isExtractRules()) {
        List<KeepDeclaration> declarations = KeepEdgeReader.readKeepEdges(classFileData);
        if (!declarations.isEmpty()) {
          KeepRuleExtractor extractor = new KeepRuleExtractor(builder::addKeepRules);
          declarations.forEach(extractor::extract);
        }
        return;
      }
      if (isNormalizeEdges()) {
        List<KeepDeclaration> declarations = KeepEdgeReader.readKeepEdges(classFileData);
        if (!declarations.isEmpty()) {
          KeepSpec.Builder keepSpecBuilder = KeepSpec.newBuilder();
          keepSpecBuilder.setVersion(KeepSpecVersion.getCurrent().buildProto());
          for (KeepDeclaration declaration : declarations) {
            keepSpecBuilder.addDeclarations(declaration.buildDeclarationProto());
          }
          builder
              .getBuilder()
              .addKeepSpecificationData(keepSpecBuilder.build().toByteArray(), Origin.unknown());
        }
        return;
      }
      assert isDirect();
    }

    @Override
    public SingleTestRunResult<?> run(Class<?> mainClass) throws Exception {
      R8TestCompileResult compileResult = builder.compile();
      compileResultConsumers.forEach(fn -> fn.accept(compileResult));
      return compileResult.run(parameters().getRuntime(), mainClass);
    }
  }

  private static class R8LegacyBuilder extends KeepAnnoTestBuilder {

    private final KeepRuleExtractorOptions extractorOptions =
        KeepRuleExtractorOptions.getR8Options();
    private final ExternalR8TestBuilder builder;
    private final List<Consumer<List<String>>> configConsumers = new ArrayList<>();

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
    public KeepAnnoTestBuilder addProgramFiles(List<Path> programFiles) throws IOException {
      List<String> rules = KeepAnnoTestUtils.extractRulesFromFiles(programFiles, extractorOptions);
      builder.addProgramFiles(programFiles);
      builder.addKeepRules(rules);
      return this;
    }

    @Override
    public KeepAnnoTestBuilder addProgramClasses(List<Class<?>> programClasses) throws IOException {
      List<String> rules = KeepAnnoTestUtils.extractRules(programClasses, extractorOptions);
      builder.addProgramClasses(programClasses);
      builder.addKeepRules(rules);
      return this;
    }

    @Override
    public KeepAnnoTestBuilder addProgramClassFileData(List<byte[]> programClasses)
        throws IOException {
      List<String> rules =
          KeepAnnoTestUtils.extractRulesFromBytes(programClasses, extractorOptions);
      builder.addProgramClassFileData(programClasses);
      builder.addKeepRules(rules);
      return this;
    }

    @Override
    public KeepAnnoTestBuilder inspectOutputConfig(Consumer<String> configConsumer) {
      configConsumers.add(lines -> configConsumer.accept(String.join("\n", lines)));
      return this;
    }

    @Override
    public SingleTestRunResult<?> run(Class<?> mainClass) throws Exception {
      configConsumers.forEach(fn -> fn.accept(builder.getConfig()));
      return builder.run(parameters().getRuntime(), mainClass);
    }
  }

  private static class PGBuilder extends KeepAnnoTestBuilder {

    private final KeepRuleExtractorOptions extractorOptions =
        KeepRuleExtractorOptions.getPgOptions();
    private final ProguardTestBuilder builder;
    private final List<Consumer<List<String>>> configConsumers = new ArrayList<>();

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
    public KeepAnnoTestBuilder addProgramFiles(List<Path> programFiles) throws IOException {
      List<String> rules = KeepAnnoTestUtils.extractRulesFromFiles(programFiles, extractorOptions);
      builder.addProgramFiles(programFiles);
      builder.addKeepRules(rules);
      return this;
    }

    @Override
    public KeepAnnoTestBuilder addProgramClasses(List<Class<?>> programClasses) throws IOException {
      List<String> rules = KeepAnnoTestUtils.extractRules(programClasses, extractorOptions);
      builder.addProgramClasses(programClasses);
      builder.addKeepRules(rules);
      return this;
    }

    @Override
    public KeepAnnoTestBuilder addProgramClassFileData(List<byte[]> programClasses)
        throws IOException {
      List<String> rules =
          KeepAnnoTestUtils.extractRulesFromBytes(programClasses, extractorOptions);
      builder.addProgramClassFileData(programClasses);
      builder.addKeepRules(rules);
      return this;
    }

    @Override
    public KeepAnnoTestBuilder inspectOutputConfig(Consumer<String> configConsumer) {
      configConsumers.add(lines -> configConsumer.accept(String.join("\n", lines)));
      return this;
    }

    @Override
    public SingleTestRunResult<?> run(Class<?> mainClass) throws Exception {
      configConsumers.forEach(fn -> fn.accept(builder.getConfig()));
      return builder.run(parameters().getRuntime(), mainClass);
    }
  }
}
