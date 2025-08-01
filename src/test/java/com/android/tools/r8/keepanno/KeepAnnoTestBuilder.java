// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno;

import static com.android.tools.r8.R8TestBuilder.KeepAnnotationLibrary.ANDROIDX;
import static com.android.tools.r8.utils.FileUtils.isClassFile;
import static com.android.tools.r8.utils.FileUtils.isJarFile;
import static com.android.tools.r8.utils.FileUtils.isZipFile;

import com.android.tools.r8.ExternalR8TestBuilder;
import com.android.tools.r8.KotlinCompilerTool.KotlinCompiler;
import com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion;
import com.android.tools.r8.ProgramResourceProvider;
import com.android.tools.r8.ProguardTestBuilder;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8PartialTestBuilder;
import com.android.tools.r8.R8PartialTestCompileResult;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.R8TestBuilder.KeepAnnotationLibrary;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.R8TestCompileResultBase;
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
import com.android.tools.r8.partial.R8PartialCompilationConfiguration.Builder;
import com.android.tools.r8.utils.ZipUtils;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import org.junit.rules.TemporaryFolder;

public abstract class KeepAnnoTestBuilder {

  public static KeepAnnoTestBuilder forKeepAnnoTest(
      KeepAnnoParameters params, TemporaryFolder temp, KeepAnnotationLibrary keepAnnotationLibrary)
      throws IOException {
    switch (params.config()) {
      case REFERENCE:
        return new ReferenceBuilder(params, keepAnnotationLibrary, temp);
      case R8_DIRECT:
      case R8_NORMALIZED:
      case R8_RULES:
        return new R8NativeBuilder(params, keepAnnotationLibrary, temp);
      case R8_PARTIAL_DIRECT:
      case R8_PARTIAL_NORMALIZED:
      case R8_PARTIAL_RULES:
        params.parameters().assumeCanUseR8Partial();
        return new R8PartialNativeBuilder(params, keepAnnotationLibrary, temp);
      case R8_LEGACY:
        return new R8LegacyBuilder(params, keepAnnotationLibrary, temp);
      case PG:
        return new PGBuilder(params, keepAnnotationLibrary, temp);
      default:
        throw new IllegalStateException("Unexpected keep anno config: " + params.config());
    }
  }

  private final KeepAnnoParameters keepAnnoParams;

  private KeepAnnoTestBuilder(KeepAnnoParameters params) {
    this.keepAnnoParams = params;
  }

  public final TestParameters parameters() {
    return keepAnnoParams.parameters();
  }

  public KeepAnnoTestBuilder addInnerClasses(Class<?> clazz) throws IOException {
    return addProgramFiles(new ArrayList<>(ToolHelper.getClassFilesForInnerClasses(clazz)));
  }

  public abstract KeepAnnoTestBuilder addProgramFiles(List<Path> programFiles) throws IOException;

  public abstract KeepAnnoTestBuilder addProgramResourceProviders(
      ProgramResourceProvider... providers);

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

  public abstract KeepAnnoTestBuilder addProgramFilesWithoutAnnotations(List<Path> programFiles)
      throws IOException;

  public final KeepAnnoTestBuilder addKeepMainRule(Class<?> mainClass) {
    return applyIfShrinker(b -> b.addKeepMainRule(mainClass));
  }

  public final KeepAnnoTestBuilder addKeepClassRules(Class<?>... classes) {
    return applyIfShrinker(b -> b.addKeepClassRules(classes));
  }

  public final KeepAnnoTestBuilder addKeepRules(String... classes) {
    return applyIfShrinker(b -> b.addKeepRules(classes));
  }

  public abstract void compile() throws Exception;

  public abstract SingleTestRunResult<?> run(Class<?> mainClass) throws Exception;

  public abstract SingleTestRunResult<?> run(String mainClass) throws Exception;

  public KeepAnnoTestBuilder applyIf(
      boolean condition, ThrowableConsumer<KeepAnnoTestBuilder> consumer) {
    if (condition) {
      consumer.acceptWithRuntimeException(this);
    }
    return this;
  }

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

  public KeepAnnoTestBuilder applyIfR8Current(
      ThrowableConsumer<R8TestBuilder<?, ?, ?>> builderConsumer) {
    return this;
  }

  public KeepAnnoTestBuilder applyIfR8Partial(
      ThrowableConsumer<R8PartialTestBuilder> builderConsumer) {
    return this;
  }

  public KeepAnnoTestBuilder applyIfR8OrR8Partial(
      ThrowableConsumer<TestShrinkerBuilder<?, ?, ?, ?, ?>> r8BuilderConsumer,
      ThrowableConsumer<R8PartialTestBuilder> r8PartialBuilderConsumer) {
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

  /**
   * Prints the all rules passed to the compilation. Compared to {@link #printExtractedRules()} this
   * is all rules, not just the ones extracted from keep annotations.
   */
  public final KeepAnnoTestBuilder printRules() {
    return inspectOutputRules(System.out::println);
  }

  /**
   * Inspect the all rules passed to the compilation. Compared to {@link
   * #inspectExtractedRules(Consumer)} this is all rules, not just the ones extracted from keep
   * annotations.
   */
  public abstract KeepAnnoTestBuilder inspectOutputRules(Consumer<String> configConsumer);

  /** Prints the rules extracted from annotations as part of the compilation. */
  public final KeepAnnoTestBuilder printExtractedRules() {
    return inspectExtractedRules(rules -> System.out.println(String.join("\n", rules)));
  }

  /** Inspect the rules extracted from annotations as part of the compilation. */
  public abstract KeepAnnoTestBuilder inspectExtractedRules(Consumer<List<String>> configConsumer);

  private static class ReferenceBuilder extends KeepAnnoTestBuilder {

    private final TestBuilder<? extends SingleTestRunResult<?>, ?> builder;

    public ReferenceBuilder(
        KeepAnnoParameters params,
        KeepAnnotationLibrary keepAnnotationLibrary,
        TemporaryFolder temp) {
      super(params);
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
    public KeepAnnoTestBuilder addProgramResourceProviders(ProgramResourceProvider... providers) {
      assert false : "not supported";
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
    public KeepAnnoTestBuilder addProgramFilesWithoutAnnotations(List<Path> programFiles)
        throws IOException {
      // TODO(b/392865072): Ensure annotations are not processed.
      builder.addProgramFiles(programFiles);
      return this;
    }

    @Override
    public KeepAnnoTestBuilder inspectOutputRules(Consumer<String> configConsumer) {
      // Ignore the consumer.
      return this;
    }

    @Override
    public KeepAnnoTestBuilder inspectExtractedRules(Consumer<List<String>> configConsumer) {
      // Ignore the consumer.
      return this;
    }

    @Override
    public void compile() throws Exception {
      // Do nothing.
    }

    @Override
    public SingleTestRunResult<?> run(Class<?> mainClass) throws Exception {
      return builder.run(parameters().getRuntime(), mainClass);
    }

    @Override
    public SingleTestRunResult<?> run(String mainClass) throws Exception {
      return builder.run(parameters().getRuntime(), mainClass);
    }
  }

  private abstract static class R8NativeBuilderBase<
          B extends R8TestBuilder<R, ?, ?>, R extends R8TestCompileResultBase<R>>
      extends KeepAnnoTestBuilder {

    final B builder;
    final KeepAnnoConfig config;

    final List<Consumer<R>> compileResultConsumers = new ArrayList<>();
    final List<String> extractedRules = new ArrayList();

    private R8NativeBuilderBase(KeepAnnoParameters params, B builder) {
      super(params);
      this.builder = builder;
      this.config = params.config();
    }

    abstract boolean isExtractRules();

    abstract boolean isNormalizeEdges();

    abstract boolean isDirect();

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
        ThrowableConsumer<R8TestBuilder<?, ?, ?>> builderConsumer) {
      builderConsumer.acceptWithRuntimeException(builder);
      return this;
    }

    @Override
    public KeepAnnoTestBuilder applyIfR8OrR8Partial(
        ThrowableConsumer<TestShrinkerBuilder<?, ?, ?, ?, ?>> r8BuilderConsumer,
        ThrowableConsumer<R8PartialTestBuilder> r8PartialBuilderConsumer) {
      r8BuilderConsumer.acceptWithRuntimeException(builder);
      return this;
    }

    @Override
    public KeepAnnoTestBuilder addProgramFiles(List<Path> programFiles) throws IOException {
      for (Path programFile : programFiles) {
        if (isClassFile(programFile)) {
          extractAndAdd(Files.readAllBytes(programFile));
        } else if (isJarFile(programFile) || isZipFile(programFile)) {
          ZipUtils.iter(
              programFile,
              (entry, input) -> {
                if (isClassFile(entry.getName())) {
                  extractAndAdd(ByteStreams.toByteArray(input));
                }
              });
        } else {
          assert false : "Unsupported file format";
        }
      }
      return this;
    }

    @Override
    public KeepAnnoTestBuilder addProgramResourceProviders(ProgramResourceProvider... providers) {
      builder.addProgramResourceProviders(Arrays.asList(providers));
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
    public KeepAnnoTestBuilder addProgramClassFileData(List<byte[]> programClasses) {
      for (byte[] programClass : programClasses) {
        extractAndAdd(programClass);
      }
      return this;
    }

    private void extractAndAdd(byte[] classFileData) {
      builder.addProgramClassFileData(classFileData);
      if (isExtractRules()) {
        List<KeepDeclaration> declarations = KeepEdgeReader.readKeepEdges(classFileData);
        if (!declarations.isEmpty()) {
          KeepRuleExtractor extractor =
              new KeepRuleExtractor(
                  rule -> {
                    builder.addKeepRules(rule);
                    extractedRules.add(rule);
                  });
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
    public KeepAnnoTestBuilder addProgramFilesWithoutAnnotations(List<Path> programFiles)
        throws IOException {
      // TODO(b/392865072): Ensure annotations are not processed.
      builder.addProgramFiles(programFiles);
      return this;
    }

    @Override
    public KeepAnnoTestBuilder inspectOutputRules(Consumer<String> configConsumer) {
      compileResultConsumers.add(
          result -> configConsumer.accept(result.getProguardConfiguration()));
      return this;
    }

    @Override
    public KeepAnnoTestBuilder inspectExtractedRules(Consumer<List<String>> configConsumer) {
      compileResultConsumers.add(result -> configConsumer.accept(extractedRules));
      return this;
    }

    @Override
    public void compile() throws Exception {
      R compileResult = builder.compile();
      compileResultConsumers.forEach(fn -> fn.accept(compileResult));
    }

    @Override
    public SingleTestRunResult<?> run(Class<?> mainClass) throws Exception {
      R compileResult = builder.compile();
      compileResultConsumers.forEach(fn -> fn.accept(compileResult));
      return compileResult.run(parameters().getRuntime(), mainClass);
    }

    @Override
    public SingleTestRunResult<?> run(String mainClass) throws Exception {
      R compileResult = builder.compile();
      compileResultConsumers.forEach(fn -> fn.accept(compileResult));
      return compileResult.run(parameters().getRuntime(), mainClass);
    }
  }

  private static class R8NativeBuilder
      extends R8NativeBuilderBase<R8FullTestBuilder, R8TestCompileResult> {

    private R8NativeBuilder(
        KeepAnnoParameters params,
        KeepAnnotationLibrary keepAnnotationLibrary,
        TemporaryFolder temp) {
      super(params, TestBase.testForR8(temp, params.getBackend()));
      builder.enableExperimentalKeepAnnotations(keepAnnotationLibrary).setMinApi(parameters());

      // TODO(b/323816623): Replace the testing flag by the API call.
      builder.getBuilder().setEnableExperimentalKeepAnnotations(false);
      builder.addOptionsModification(o -> o.testing.enableEmbeddedKeepAnnotations = isDirect());
    }

    @Override
    boolean isExtractRules() {
      return config == KeepAnnoConfig.R8_RULES;
    }

    @Override
    boolean isNormalizeEdges() {
      return config == KeepAnnoConfig.R8_NORMALIZED;
    }

    @Override
    boolean isDirect() {
      return config == KeepAnnoConfig.R8_DIRECT;
    }
  }

  private static class R8PartialNativeBuilder
      extends R8NativeBuilderBase<R8PartialTestBuilder, R8PartialTestCompileResult> {

    private R8PartialNativeBuilder(
        KeepAnnoParameters params,
        KeepAnnotationLibrary keepAnnotationLibrary,
        TemporaryFolder temp) {
      super(params, TestBase.testForR8Partial(temp));

      builder
          .setR8PartialConfiguration(Builder::includeAll)
          .enableExperimentalKeepAnnotations(keepAnnotationLibrary)
          .setMinApi(parameters());

      // TODO(b/323816623): Replace the testing flag by the API call.
      builder.getBuilder().setEnableExperimentalKeepAnnotations(false);
      builder.addR8PartialOptionsModification(
          o -> o.testing.enableEmbeddedKeepAnnotations = isDirect());
    }

    @Override
    public KeepAnnoTestBuilder applyIfR8Partial(
        ThrowableConsumer<R8PartialTestBuilder> builderConsumer) {
      builderConsumer.acceptWithRuntimeException(builder);
      return this;
    }

    @Override
    public KeepAnnoTestBuilder applyIfR8OrR8Partial(
        ThrowableConsumer<TestShrinkerBuilder<?, ?, ?, ?, ?>> r8BuilderConsumer,
        ThrowableConsumer<R8PartialTestBuilder> r8PartialBuilderConsumer) {
      r8PartialBuilderConsumer.acceptWithRuntimeException(builder);
      return this;
    }

    @Override
    boolean isExtractRules() {
      return config == KeepAnnoConfig.R8_PARTIAL_RULES;
    }

    @Override
    boolean isNormalizeEdges() {
      return config == KeepAnnoConfig.R8_PARTIAL_NORMALIZED;
    }

    @Override
    boolean isDirect() {
      return config == KeepAnnoConfig.R8_PARTIAL_DIRECT;
    }
  }

  private static class R8LegacyBuilder extends KeepAnnoTestBuilder {

    private final KeepRuleExtractorOptions extractorOptions =
        KeepRuleExtractorOptions.getR8Options();
    private final ExternalR8TestBuilder builder;
    private final List<Consumer<List<String>>> configConsumers = new ArrayList<>();
    private final List<Consumer<List<String>>> extractedRulesConsumers = new ArrayList<>();
    private final List<String> extractedRules = new ArrayList();

    public R8LegacyBuilder(
        KeepAnnoParameters params,
        KeepAnnotationLibrary keepAnnotationLibrary,
        TemporaryFolder temp)
        throws IOException {
      super(params);
      builder =
          TestBase.testForExternalR8(temp, parameters().getBackend())
              .useProvidedR8(KeepAnnoTestUtils.R8_LIB)
              .addProgramFiles(KeepAnnoTestUtils.getKeepAnnoLib(temp, keepAnnotationLibrary))
              .setMinApi(parameters());
    }

    @Override
    public KeepAnnoTestBuilder applyIfR8(
        ThrowableConsumer<TestShrinkerBuilder<?, ?, ?, ?, ?>> builderConsumer) {
      builderConsumer.acceptWithRuntimeException(builder);
      return this;
    }

    @Override
    public KeepAnnoTestBuilder applyIfR8OrR8Partial(
        ThrowableConsumer<TestShrinkerBuilder<?, ?, ?, ?, ?>> r8BuilderConsumer,
        ThrowableConsumer<R8PartialTestBuilder> r8PartialBuilderConsumer) {
      r8BuilderConsumer.acceptWithRuntimeException(builder);
      return this;
    }

    @Override
    public KeepAnnoTestBuilder addProgramFiles(List<Path> programFiles) throws IOException {
      List<String> rules = KeepAnnoTestUtils.extractRulesFromFiles(programFiles, extractorOptions);
      builder.addProgramFiles(programFiles);
      builder.addKeepRules(rules);
      extractedRules.addAll(rules);
      return this;
    }

    @Override
    public KeepAnnoTestBuilder addProgramResourceProviders(ProgramResourceProvider... providers) {
      builder.addProgramResourceProviders(Arrays.asList(providers));
      return this;
    }

    @Override
    public KeepAnnoTestBuilder addProgramClasses(List<Class<?>> programClasses) throws IOException {
      List<String> rules = KeepAnnoTestUtils.extractRules(programClasses, extractorOptions);
      builder.addProgramClasses(programClasses);
      builder.addKeepRules(rules);
      extractedRules.addAll(rules);
      return this;
    }

    @Override
    public KeepAnnoTestBuilder addProgramClassFileData(List<byte[]> programClasses)
        throws IOException {
      List<String> rules =
          KeepAnnoTestUtils.extractRulesFromBytes(programClasses, extractorOptions);
      builder.addProgramClassFileData(programClasses);
      builder.addKeepRules(rules);
      extractedRules.addAll(rules);
      return this;
    }

    @Override
    public KeepAnnoTestBuilder addProgramFilesWithoutAnnotations(List<Path> programFiles)
        throws IOException {
      // TODO(b/392865072): Ensure annotations are not processed.
      builder.addProgramFiles(programFiles);
      return this;
    }

    @Override
    public KeepAnnoTestBuilder inspectOutputRules(Consumer<String> configConsumer) {
      configConsumers.add(lines -> configConsumer.accept(String.join("\n", lines)));
      return this;
    }

    public KeepAnnoTestBuilder inspectExtractedRules(Consumer<List<String>> configConsumer) {
      extractedRulesConsumers.add(configConsumer);
      return this;
    }

    @Override
    public void compile() throws Exception {
      builder.compile();
    }

    @Override
    public SingleTestRunResult<?> run(Class<?> mainClass) throws Exception {
      configConsumers.forEach(fn -> fn.accept(builder.getConfig()));
      extractedRulesConsumers.forEach(fn -> fn.accept(extractedRules));
      return builder.run(parameters().getRuntime(), mainClass);
    }

    @Override
    public SingleTestRunResult<?> run(String mainClass) throws Exception {
      configConsumers.forEach(fn -> fn.accept(builder.getConfig()));
      extractedRulesConsumers.forEach(fn -> fn.accept(extractedRules));
      return builder.run(parameters().getRuntime(), mainClass);
    }
  }

  private static class PGBuilder extends KeepAnnoTestBuilder {

    private final KeepRuleExtractorOptions extractorOptions =
        KeepRuleExtractorOptions.getPgOptions();
    private final ProguardTestBuilder builder;
    private final List<Consumer<List<String>>> configConsumers = new ArrayList<>();
    private final List<Consumer<List<String>>> extractedRulesConsumers = new ArrayList<>();
    private final List<String> extractedRules = new ArrayList<>();

    public PGBuilder(
        KeepAnnoParameters params,
        KeepAnnotationLibrary keepAnnotationLibrary,
        TemporaryFolder temp)
        throws IOException {
      super(params);
      KotlinCompiler kotlinc = new KotlinCompiler(KotlinCompilerVersion.MAX_SUPPORTED_VERSION);
      builder =
          TestBase.testForProguard(KeepAnnoTestUtils.PG_VERSION, temp)
              .applyIf(
                  keepAnnotationLibrary == ANDROIDX, b -> b.addDefaultRuntimeLibrary(parameters()))
              .addProgramFiles(KeepAnnoTestUtils.getKeepAnnoLib(temp, keepAnnotationLibrary))
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
      extractedRules.addAll(rules);
      return this;
    }

    @Override
    public KeepAnnoTestBuilder addProgramResourceProviders(ProgramResourceProvider... providers) {
      builder.addProgramResourceProviders(Arrays.asList(providers));
      return this;
    }

    @Override
    public KeepAnnoTestBuilder addProgramClasses(List<Class<?>> programClasses) throws IOException {
      List<String> rules = KeepAnnoTestUtils.extractRules(programClasses, extractorOptions);
      builder.addProgramClasses(programClasses);
      builder.addKeepRules(rules);
      extractedRules.addAll(rules);
      return this;
    }

    @Override
    public KeepAnnoTestBuilder addProgramClassFileData(List<byte[]> programClasses)
        throws IOException {
      List<String> rules =
          KeepAnnoTestUtils.extractRulesFromBytes(programClasses, extractorOptions);
      builder.addProgramClassFileData(programClasses);
      builder.addKeepRules(rules);
      extractedRules.addAll(rules);
      return this;
    }

    @Override
    public KeepAnnoTestBuilder addProgramFilesWithoutAnnotations(List<Path> programFiles)
        throws IOException {
      // TODO(b/392865072): Ensure annotations are not processed.
      builder.addProgramFiles(programFiles);
      return this;
    }

    @Override
    public KeepAnnoTestBuilder inspectOutputRules(Consumer<String> configConsumer) {
      configConsumers.add(lines -> configConsumer.accept(String.join("\n", lines)));
      return this;
    }

    public KeepAnnoTestBuilder inspectExtractedRules(Consumer<List<String>> configConsumer) {
      extractedRulesConsumers.add(configConsumer);
      return this;
    }

    @Override
    public void compile() throws Exception {
      builder.compile();
    }

    @Override
    public SingleTestRunResult<?> run(Class<?> mainClass) throws Exception {
      configConsumers.forEach(fn -> fn.accept(builder.getConfig()));
      extractedRulesConsumers.forEach(fn -> fn.accept(extractedRules));
      return builder.run(parameters().getRuntime(), mainClass);
    }

    @Override
    public SingleTestRunResult<?> run(String mainClass) throws Exception {
      configConsumers.forEach(fn -> fn.accept(builder.getConfig()));
      extractedRulesConsumers.forEach(fn -> fn.accept(extractedRules));
      return builder.run(parameters().getRuntime(), mainClass);
    }
  }
}
