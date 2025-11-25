// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.utils.InternalOptions.DETERMINISTIC_DEBUGGING;
import static com.android.tools.r8.utils.MapConsumerUtils.wrapExistingInternalMapConsumerIfNotNull;

import com.android.build.shrinker.r8integration.LegacyResourceShrinker;
import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.dex.Marker.Tool;
import com.android.tools.r8.dump.DumpOptions;
import com.android.tools.r8.errors.DexFileOverflowDiagnostic;
import com.android.tools.r8.experimental.graphinfo.GraphConsumer;
import com.android.tools.r8.features.FeatureSplitConfiguration;
import com.android.tools.r8.features.FeatureSplitProgramResourceProvider;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.inspector.Inspector;
import com.android.tools.r8.inspector.internal.InspectorImpl;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.LibraryDesugaringOptions;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.keepanno.asm.KeepEdgeReader;
import com.android.tools.r8.keepanno.ast.KeepDeclaration;
import com.android.tools.r8.keepanno.keeprules.KeepRuleExtractor;
import com.android.tools.r8.metadata.R8BuildMetadata;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.ClassNamingForNameMapper;
import com.android.tools.r8.naming.InternalMapConsumer;
import com.android.tools.r8.naming.InternalMapConsumerImpl;
import com.android.tools.r8.naming.SourceFileRewriter;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.partial.R8PartialCompilationConfiguration;
import com.android.tools.r8.profile.art.ArtProfileForRewriting;
import com.android.tools.r8.shaking.FilteredClassPath;
import com.android.tools.r8.shaking.KeepSpecificationSource;
import com.android.tools.r8.shaking.ProguardConfiguration;
import com.android.tools.r8.shaking.ProguardConfigurationParser;
import com.android.tools.r8.shaking.ProguardConfigurationParserOptions;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import com.android.tools.r8.shaking.ProguardConfigurationSource;
import com.android.tools.r8.shaking.ProguardConfigurationSourceFile;
import com.android.tools.r8.shaking.ProguardConfigurationSourceStrings;
import com.android.tools.r8.startup.StartupProfileProvider;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.ArchiveResourceProvider;
import com.android.tools.r8.utils.AssertionConfigurationWithDefault;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.DumpInputFlags;
import com.android.tools.r8.utils.EmbeddedRulesExtractor;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.InternalOptions.DesugarState;
import com.android.tools.r8.utils.InternalOptions.HorizontalClassMergerOptions;
import com.android.tools.r8.utils.InternalOptions.LineNumberOptimization;
import com.android.tools.r8.utils.InternalOptions.MappingComposeOptions;
import com.android.tools.r8.utils.InternalProgramClassProvider;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.MapConsumerUtils;
import com.android.tools.r8.utils.ProgramClassCollection;
import com.android.tools.r8.utils.ProgramResourceProviderUtils;
import com.android.tools.r8.utils.ProgramResourceUtils;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.SemanticVersion;
import com.android.tools.r8.utils.SemanticVersionUtils;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ThreadUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Immutable command structure for an invocation of the {@link R8} compiler.
 *
 * <p>To build a R8 command use the {@link R8Command.Builder} class. For example:
 *
 * <pre>
 *   R8Command command = R8Command.builder()
 *     .addProgramFiles(path1, path2)
 *     .setMode(CompilationMode.RELEASE)
 *     .setOutput(Paths.get("output.zip", OutputMode.DexIndexed))
 *     .build();
 * </pre>
 */
@KeepForApi
public final class R8Command extends BaseCompilerCommand {

  /**
   * Builder for constructing a R8Command.
   *
   * <p>A builder is obtained by calling {@link R8Command#builder}.
   */
  @KeepForApi
  public static class Builder extends BaseCompilerCommand.Builder<R8Command, Builder> {

    private static class DefaultR8DiagnosticsHandler implements DiagnosticsHandler {

      @Override
      public void error(Diagnostic error) {
        if (error instanceof DexFileOverflowDiagnostic) {
          DexFileOverflowDiagnostic overflowDiagnostic = (DexFileOverflowDiagnostic) error;
          if (!overflowDiagnostic.hasMainDexSpecification()) {
            DiagnosticsHandler.super.error(
                new StringDiagnostic(
                    overflowDiagnostic.getDiagnosticMessage()
                        + ". Try supplying a main-dex list or main-dex rules"));
            return;
          }
        }
        DiagnosticsHandler.super.error(error);
      }
    }

    private final List<ProguardConfigurationSource> mainDexRules = new ArrayList<>();
    private Consumer<ProguardConfiguration.Builder> proguardConfigurationConsumerForTesting = null;
    private Consumer<List<ProguardConfigurationRule>> syntheticProguardRulesConsumer = null;
    private final List<KeepSpecificationSource> keepSpecifications = new ArrayList<>();
    private final List<ProguardConfigurationSource> proguardConfigs = new ArrayList<>();
    private boolean disableTreeShaking = false;
    private boolean disableMinification = false;
    private boolean forceProguardCompatibility = false;
    private boolean protectApiSurface = false;
    private Optional<Boolean> includeDataResources = Optional.empty();
    private StringConsumer proguardUsageConsumer = null;
    private StringConsumer proguardSeedsConsumer = null;
    private StringConsumer proguardConfigurationConsumer = null;
    private GraphConsumer keptGraphConsumer = null;
    private GraphConsumer mainDexKeptGraphConsumer = null;
    private InputDependencyGraphConsumer inputDependencyGraphConsumer = null;
    private Consumer<? super R8BuildMetadata> buildMetadataConsumer = null;
    private final FeatureSplitConfiguration.Builder featureSplitConfigurationBuilder =
        FeatureSplitConfiguration.builder();
    private String synthesizedClassPrefix =
        System.getProperty("com.android.tools.r8.synthesizedClassPrefix", "");
    private boolean enableMissingLibraryApiModeling = false;
    private boolean enableExperimentalKeepAnnotations =
        System.getProperty("com.android.tools.r8.enableKeepAnnotations") != null;
    private boolean readEmbeddedRulesFromClasspathAndLibrary =
        System.getProperty("com.android.tools.r8.readEmbeddedRulesFromClasspathAndLibrary") != null;
    public boolean enableStartupLayoutOptimization = true;
    private SemanticVersion fakeCompilerVersion = null;
    private AndroidResourceProvider androidResourceProvider = null;
    private AndroidResourceConsumer androidResourceConsumer = null;
    private ResourceShrinkerConfiguration resourceShrinkerConfiguration =
        ResourceShrinkerConfiguration.DEFAULT_CONFIGURATION;
    private R8PartialCompilationConfiguration partialCompilationConfiguration =
        R8PartialCompilationConfiguration.fromSystemProperties();
    private final List<PartialOptimizationConfigurationProvider>
        partialOptimizationConfigurationProviders = new ArrayList<>();

    private final ProguardConfigurationParserOptions.Builder parserOptionsBuilder =
        ProguardConfigurationParserOptions.builder().readEnvironment();
    private final boolean allowDexInputToR8 =
        System.getProperty("com.android.tools.r8.allowDexInputToR8") != null;

    // TODO(zerny): Consider refactoring CompatProguardCommandBuilder to avoid subclassing.
    Builder() {
      this(new DefaultR8DiagnosticsHandler());
    }

    Builder(DiagnosticsHandler diagnosticsHandler) {
      super(diagnosticsHandler);
      setIgnoreDexInArchive(!allowDexInputToR8);
    }

    private Builder(AndroidApp app) {
      super(app);
      setIgnoreDexInArchive(!allowDexInputToR8);
    }

    private Builder(AndroidApp app, DiagnosticsHandler diagnosticsHandler) {
      super(app, diagnosticsHandler);
      setIgnoreDexInArchive(!allowDexInputToR8);
    }

    // Internal

    @Override
    Builder self() {
      return this;
    }

    @Override
    CompilationMode defaultCompilationMode() {
      return CompilationMode.RELEASE;
    }

    Builder setSynthesizedClassesPrefix(String prefix) {
      synthesizedClassPrefix = prefix;
      return self();
    }

    Builder setFakeCompilerVersion(SemanticVersion version) {
      fakeCompilerVersion = version;
      return self();
    }

    /**
     * Disable tree shaking.
     *
     * <p>If true, tree shaking is completely disabled, otherwise tree shaking is configured by
     * ProGuard configuration settings.
     */
    public Builder setDisableTreeShaking(boolean disableTreeShaking) {
      this.disableTreeShaking = disableTreeShaking;
      return self();
    }

    /**
     * Disable minification of names.
     *
     * <p>If true, minification of names is completely disabled, otherwise minification of names is
     * configured by ProGuard configuration settings.
     */
    public Builder setDisableMinification(boolean disableMinification) {
      this.disableMinification = disableMinification;
      return self();
    }

    /** Add proguard configuration files with rules for automatic main-dex-list calculation. */
    public Builder addMainDexRulesFiles(Path... paths) {
      return addMainDexRulesFiles(Arrays.asList(paths));
    }

    /** Add proguard configuration files with rules for automatic main-dex-list calculation. */
    public Builder addMainDexRulesFiles(Collection<Path> paths) {
      guard(() -> paths.forEach(p -> mainDexRules.add(new ProguardConfigurationSourceFile(p))));
      return self();
    }

    /** Add proguard rules for automatic main-dex-list calculation. */
    public Builder addMainDexRules(List<String> lines, Origin origin) {
      String config = String.join(System.lineSeparator(), lines);
      mainDexRules.add(new ProguardConfigurationSourceStrings(config, Paths.get("."), origin));
      return self();
    }

    public Builder addKeepSpecificationFiles(Path... paths) {
      return addKeepSpecificationFiles(Arrays.asList(paths));
    }

    public Builder addKeepSpecificationFiles(Collection<Path> paths) {
      paths.forEach(p -> keepSpecifications.add(KeepSpecificationSource.fromFile(p)));
      return self();
    }

    public Builder addKeepSpecificationData(byte[] data, Origin origin) {
      keepSpecifications.add(KeepSpecificationSource.fromBytes(origin, data));
      return self();
    }

    /**
     * Set Proguard compatibility mode.
     *
     * <p>If true, R8 will attempt to retain more compatibility with Proguard. Most notably, R8 will
     * introduce rules for keeping more default constructors as well as various attributes. Note
     * that setting R8 in compatibility mode will result in larger residual programs.
     */
    public Builder setProguardCompatibility(boolean value) {
      this.forceProguardCompatibility = value;
      return self();
    }

    /** Get the current value of Proguard compatibility mode. */
    public boolean getProguardCompatibility() {
      return forceProguardCompatibility;
    }

    /**
     * Option to protect the API surface of the given compilation unit.
     *
     * <p>If true, R8 will not add public or protected members to classes that are kept or have kept
     * subclasses, to not alter the public API surface of the compilation unit.
     *
     * <p>Defaults to true when compiling to class files.
     */
    public Builder setProtectApiSurface(boolean value) {
      this.protectApiSurface = value;
      return self();
    }

    /** Add proguard configuration-file resources. */
    public Builder addProguardConfigurationFiles(Path... paths) {
      guard(() -> {
        for (Path path : paths) {
          proguardConfigs.add(new ProguardConfigurationSourceFile(path));
        }
      });
      return self();
    }

    /** Add proguard configuration-file resources. */
    public Builder addProguardConfigurationFiles(List<Path> paths) {
      guard(() -> {
        for (Path path : paths) {
          proguardConfigs.add(new ProguardConfigurationSourceFile(path));
        }
      });
      return self();
    }

    /** Add proguard configuration. */
    public Builder addProguardConfiguration(List<String> lines, Origin origin) {
      String config = String.join(System.lineSeparator(), lines);
      proguardConfigs.add(new ProguardConfigurationSourceStrings(config, Paths.get("."), origin));
      return self();
    }

    /**
     * Set an output destination to which proguard-map content should be written.
     *
     * <p>This is a short-hand for setting a {@link StringConsumer.FileConsumer} using {@link
     * #setProguardMapConsumer}. Note that any subsequent call to this method or {@link
     * #setProguardMapConsumer} will override the previous setting.
     *
     * @param proguardMapOutput File-system path to write output at.
     */
    @Override
    public Builder setProguardMapOutputPath(Path proguardMapOutput) {
      return super.setProguardMapOutputPath(proguardMapOutput);
    }

    /** Set input proguard map used for distribution of classes in multi-DEX. */
    public Builder setProguardMapInputFile(Path proguardInputMap) {
      getAppBuilder().setProguardMapInputData(proguardInputMap);
      return self();
    }

    /**
     * Set a consumer for receiving the proguard-map content.
     *
     * <p>It is possible to also retrieve the map id by passing an instance of {@link
     * com.android.tools.r8.MapConsumer}.
     *
     * <p>Note that any subsequent call to this method or {@link #setProguardMapOutputPath} will
     * override the previous setting.
     *
     * @param proguardMapConsumer Consumer to receive the content once produced.
     */
    @Override
    public Builder setProguardMapConsumer(StringConsumer proguardMapConsumer) {
      return super.setProguardMapConsumer(proguardMapConsumer);
    }

    /**
     * Set a consumer for receiving the proguard usage information.
     *
     * <p>Note that any subsequent calls to this method will replace the previous setting.
     *
     * @param proguardUsageConsumer Consumer to receive usage information.
     */
    public Builder setProguardUsageConsumer(StringConsumer proguardUsageConsumer) {
      this.proguardUsageConsumer = proguardUsageConsumer;
      return self();
    }

    /**
     * Set a consumer for receiving the proguard seeds information.
     *
     * <p>Note that any subsequent calls to this method will replace the previous setting.
     *
     * @param proguardSeedsConsumer Consumer to receive seeds information.
     */
    public Builder setProguardSeedsConsumer(StringConsumer proguardSeedsConsumer) {
      this.proguardSeedsConsumer = proguardSeedsConsumer;
      return self();
    }

    /**
     * Set a consumer for receiving the proguard configuration information.
     *
     * <p>Note that any subsequent calls to this method will replace the previous setting.
     * @param proguardConfigurationConsumer
     */
    public Builder setProguardConfigurationConsumer(StringConsumer proguardConfigurationConsumer) {
      this.proguardConfigurationConsumer = proguardConfigurationConsumer;
      return self();
    }

    /** Get the consumer for receiving the proguard configuration information if set. */
    public StringConsumer getProguardConfigurationConsumer() {
      return proguardConfigurationConsumer;
    }

    /**
     * Set a consumer for receiving kept-graph events.
     */
    public Builder setKeptGraphConsumer(GraphConsumer graphConsumer) {
      this.keptGraphConsumer = graphConsumer;
      return self();
    }

    /**
     * Set a consumer for receiving kept-graph events for the content of the main-dex output.
     */
    public Builder setMainDexKeptGraphConsumer(GraphConsumer graphConsumer) {
      this.mainDexKeptGraphConsumer = graphConsumer;
      return self();
    }

    /**
     * Set a consumer for receiving dependency edges for files referenced from inputs.
     *
     * <p>Note that these dependency edges are for files read when reading/parsing other input
     * files, such as the proguard configuration files. For compilation dependencies for incremental
     * desugaring see {@code setDesugarGraphConsumer}.
     */
    public Builder setInputDependencyGraphConsumer(
        InputDependencyGraphConsumer inputDependencyGraphConsumer) {
      this.inputDependencyGraphConsumer = inputDependencyGraphConsumer;
      return self();
    }

    /**
     * Set a consumer for receiving metadata about the current build intended for being stored in
     * the app bundle.
     */
    public Builder setBuildMetadataConsumer(
        Consumer<? super R8BuildMetadata> buildMetadataConsumer) {
      this.buildMetadataConsumer = buildMetadataConsumer;
      return self();
    }

    /**
     * Set the output path-and-mode.
     *
     * <p>Setting the output path-and-mode will override any previous set consumer or any previous
     * output path-and-mode, and implicitly sets the appropriate program consumer to write the
     * output.
     *
     * <p>By default data resources from the input will be included in the output. (see {@link
     * #setOutput(Path, OutputMode, boolean) for details}
     *
     * @param outputPath Path to write the output to. Must be an archive or and existing directory.
     * @param outputMode Mode in which to write the output.
     */
    @Override
    public Builder setOutput(Path outputPath, OutputMode outputMode) {
      setOutput(outputPath, outputMode, true);
      return self();
    }

    /**
     * Set the output path-and-mode and control if data resources are included.
     *
     * <p>In addition to setting the output path-and-mode (see {@link #setOutput(Path, OutputMode)})
     * this can control if data resources should be included or not.
     *
     * <p>Data resources are non Java classfile items in the input.
     *
     * <p>If data resources are not included they are ignored in the input and will not produce
     * anything in the output. If data resources are included they are processed according to the
     * configuration and written to the output.
     *
     * @param outputPath Path to write the output to. Must be an archive or and existing directory.
     * @param outputMode Mode in which to write the output.
     * @param includeDataResources If data resources from the input should be included in the
     *     output.
     */
    @Override
    public Builder setOutput(Path outputPath, OutputMode outputMode, boolean includeDataResources) {
      this.includeDataResources = Optional.of(includeDataResources);
      return super.setOutput(outputPath, outputMode, includeDataResources);
    }

    @Override
    public Builder addProgramResourceProvider(ProgramResourceProvider programProvider) {
      if (programProvider instanceof InternalProgramClassProvider) {
        InternalProgramClassProvider internalProgramProvider =
            (InternalProgramClassProvider) programProvider;
        assert verifyNonDexProgramResourceProvider(internalProgramProvider);
        assert internalProgramProvider.getDataResourceProvider() == null;
        return super.addProgramResourceProvider(internalProgramProvider);
      } else {
        return super.addProgramResourceProvider(
            new EnsureNonDexProgramResourceProvider(programProvider));
      }
    }

    private boolean verifyNonDexProgramResourceProvider(
        InternalProgramClassProvider internalProgramProvider) {
      for (DexProgramClass clazz : internalProgramProvider.getClasses()) {
        for (DexEncodedMethod method : clazz.methods()) {
          if (!method.hasCode()) {
            continue;
          }
          assert !method.getCode().isDexCode()
              : "Unexpected method with DEX code: " + method.toSourceString();
        }
      }
      return true;
    }

    /**
     * Add a {@link FeatureSplit} to the app. The {@link FeatureSplit} contains input and output
     * providers, that enables us to generate dynamic apps with optional modules.
     *
     * @param featureSplitGenerator A function that uses the supplied {@link FeatureSplit.Builder}
     *     to generate a {@link FeatureSplit}.
     */
    public Builder addFeatureSplit(
        Function<FeatureSplit.Builder, FeatureSplit> featureSplitGenerator) {
      FeatureSplit featureSplit = featureSplitGenerator.apply(FeatureSplit.builder());
      List<FeatureSplitProgramResourceProvider> featureSplitProgramResourceProviders =
          ListUtils.map(
              featureSplit.getProgramResourceProviders(), FeatureSplitProgramResourceProvider::new);
      featureSplitConfigurationBuilder.addFeatureSplit(
          featureSplit, featureSplitProgramResourceProviders);
      featureSplitProgramResourceProviders.forEach(this::addProgramResourceProvider);
      return self();
    }

    /**
     * Used to disable that keep rules with no member rules are implicitly converted into rules that
     * keep the default instance constructor.
     *
     * <p>This currently defaults to true in stable versions.
     */
    public Builder enableLegacyFullModeForKeepRules(boolean enableLegacyFullModeForKeepRules) {
      parserOptionsBuilder.setEnableLegacyFullModeForKeepRules(enableLegacyFullModeForKeepRules);
      return this;
    }

    public Builder enableLegacyFullModeForKeepRulesWarnings(
        boolean enableLegacyFullModeForKeepRulesWarnings) {
      parserOptionsBuilder.setEnableLegacyFullModeForKeepRulesWarnings(
          enableLegacyFullModeForKeepRulesWarnings);
      return this;
    }

    /**
     * Used to specify if the application is using isolated splits, i.e., if split APKs installed
     * for this application are loaded into their own Context objects.
     *
     * <p>See also <a href="https://developer.android.com/reference/android/R.attr#isolatedSplits">
     * R.attr#isolatedSplits</a>.
     */
    public Builder setEnableIsolatedSplits(boolean enableIsolatedSplits) {
      featureSplitConfigurationBuilder.setEnableIsolatedSplits(enableIsolatedSplits);
      return this;
    }

    @Deprecated
    @SuppressWarnings("InlineMeSuggester")
    public Builder setEnableExperimentalIsolatedSplits(boolean enableIsolatedSplits) {
      return setEnableIsolatedSplits(enableIsolatedSplits);
    }

    /**
     * Enable experimental/pre-release support for modeling missing library APIs.
     *
     * <p>This allows enabling the feature while it is still default disabled by the compiler. Once
     * the feature is default enabled, calling this method will have no affect.
     */
    @Deprecated
    public Builder setEnableExperimentalMissingLibraryApiModeling(boolean enable) {
      this.enableMissingLibraryApiModeling = enable;
      return self();
    }

    @Deprecated
    public Builder setEnableExperimentalKeepAnnotations(boolean enable) {
      this.enableExperimentalKeepAnnotations = enable;
      return self();
    }

    // Package private only for testing. Use system property
    // com.android.tools.r8.readEmbeddedRulesFromClasspathAndLibrary to enable.
    @Deprecated
    void setReadEmbeddedRulesFromClasspathAndLibrary(boolean enable) {
      this.readEmbeddedRulesFromClasspathAndLibrary = enable;
    }

    /**
     * Configure partial shrinking in R8, where R8 is only applied to a part of the input.
     *
     * <p>The patterns in {@code includePatterns} and {@code excludePatterns} are comma separated
     * lists of string patterns of fully qualified names of packages/classes. The patterns support
     * the wildcards {@code *} and {@code **}. The wildcards are only supported at the end of the
     * pattern, so only prefix matching. If the character just before the wildcard is a {@code .}
     * (package separator) then the difference between {@code *} and {@code **} is that {@code *}
     * only includes classes in the same package, whereas {@code **} includes classes in subpackages
     * as well. If the character before the wildcard is not a {@code .} (package separator) then
     * {@code *} and {@code **} will both match all classes with that prefix.
     *
     * <p>If {@code includePatterns} is not specified ({@code null} or an empty string), the default
     * is {@code "androidx.**,kotlin.**,kotlinx.**"}.
     *
     * <p>The include patterns are processed first collecting all possible include classes. Then the
     * exclude patterns are applied removing all matching classes from the collected include
     * classes.
     *
     * @param includePatterns patterns for classes to include in R8 shrinking (see above for
     *     semantics)
     * @param excludePatterns patterns for classes to exclude from R8 shrinking (see above for
     *     semantics)
     * @return
     */
    @Deprecated
    public Builder enableExperimentalPartialShrinking(
        String includePatterns, String excludePatterns) {
      if (partialCompilationConfiguration.isEnabled()) {
        getReporter().warning("Overriding partial compilation specified in system properties.");
      }
      if (includePatterns == null || includePatterns.isEmpty()) {
        includePatterns = "androidx.**,kotlin.**,kotlinx.**";
      }
      partialCompilationConfiguration =
          R8PartialCompilationConfiguration.fromIncludeExcludePatterns(
              includePatterns, excludePatterns);
      return self();
    }

    Builder setPartialCompilationConfiguration(
        R8PartialCompilationConfiguration partialCompilationConfiguration) {
      this.partialCompilationConfiguration = partialCompilationConfiguration;
      return this;
    }

    /**
     * Add {@link PartialOptimizationConfigurationProvider}s to enable R8 optimizations on a part of
     * the program only. The {@link PartialOptimizationConfigurationProvider}s specifies exactly
     * which classes R8 can optimize.
     *
     * @param providers instances implementing {@link PartialOptimizationConfigurationProvider} to
     *     provide the partial compilation configuration.
     */
    public Builder addPartialOptimizationConfigurationProviders(
        PartialOptimizationConfigurationProvider... providers) {
      partialOptimizationConfigurationProviders.addAll(Arrays.asList(providers));
      return this;
    }

    /**
     * Add a collection of startup profile providers that should be used for distributing the
     * program classes in DEX. The given startup profiles are also used to disallow optimizations
     * across the startup and post-startup boundary.
     *
     * <p>NOTE: Startup profiles are ignored when compiling to class files or the min-API level does
     * not support native multi-DEX (API<=20).
     */
    @Override
    public Builder addStartupProfileProviders(StartupProfileProvider... startupProfileProviders) {
      return super.addStartupProfileProviders(startupProfileProviders);
    }

    /**
     * Add a collection of startup profile providers that should be used for distributing the
     * program classes in DEX, unless turned off using {@link #setEnableStartupLayoutOptimization}.
     * The given startup profiles are also used to disallow optimizations across the startup and
     * post-startup boundary.
     *
     * <p>NOTE: Startup profiles are ignored when compiling to class files or the min-API level does
     * not support native multi-DEX (API<=20).
     */
    @Override
    public Builder addStartupProfileProviders(
        Collection<StartupProfileProvider> startupProfileProviders) {
      return super.addStartupProfileProviders(startupProfileProviders);
    }

    /**
     * API for specifying whether R8 should use the provided startup profiles to layout the DEX.
     * When this is set to {@code false}, the given startup profiles are then only used to disallow
     * optimizations across the startup and post-startup boundary.
     *
     * <p>Defaults to true.
     */
    public Builder setEnableStartupLayoutOptimization(boolean enable) {
      enableStartupLayoutOptimization = enable;
      return this;
    }

    /**
     * Exprimental API for supporting android resource shrinking.
     *
     * <p>Add an android resource provider, providing the resource table, manifest and res table
     * entries.
     */
    public Builder setAndroidResourceProvider(AndroidResourceProvider provider) {
      this.androidResourceProvider = provider;
      return this;
    }

    /**
     * Exprimental API for supporting android resource shrinking.
     *
     * <p>Add an android resource consumer, consuming the resource table, manifest and res table
     * entries.
     */
    public Builder setAndroidResourceConsumer(AndroidResourceConsumer consumer) {
      this.androidResourceConsumer = consumer;
      return this;
    }

    /**
     * API for configuring resource shrinking.
     *
     * <p>Set the configuration properties on the provided builder.
     */
    public Builder setResourceShrinkerConfiguration(
        Function<ResourceShrinkerConfiguration.Builder, ResourceShrinkerConfiguration>
            configurationBuilder) {
      this.resourceShrinkerConfiguration =
          configurationBuilder.apply(ResourceShrinkerConfiguration.builder(getReporter()));
      return this;
    }

    /**
     * By default, R8 uses the same naming scheme for synthetic classes as javac uses for anonymous
     * inner classes. By enabling verbose synthetic names, the synthetic classes will include a
     * "$$ExternalSynthetic" marker, which includes the synthetic kind (e.g., "Lambda").
     */
    @Override
    public Builder setEnableVerboseSyntheticNames(boolean enableVerboseSyntheticNames) {
      return super.setEnableVerboseSyntheticNames(enableVerboseSyntheticNames);
    }

    @Override
    void validate() {
      if (isPrintHelp()) {
        return;
      }
      Reporter reporter = getReporter();
      if (getProgramConsumer() instanceof DexFilePerClassFileConsumer) {
        reporter.error("R8 does not support compiling to a single DEX file per Java class file");
      }
      if (getMainDexListConsumer() != null && !hasMainDexList() && !hasMainDexRules()) {
        reporter.error(
            "Option --main-dex-list-output requires --main-dex-rules and/or --main-dex-list");
      }
      if (!(getProgramConsumer() instanceof ClassFileConsumer) && hasNativeMultidex()) {
        if (getMainDexListConsumer() != null || hasMainDexRules() || hasMainDexList()) {
          reporter.error(
              "R8 does not support main-dex inputs and outputs when compiling to API level "
                  + AndroidApiLevel.L.getLevel()
                  + " and above");
        }
      }
      for (FeatureSplit featureSplit : featureSplitConfigurationBuilder.getFeatureSplits()) {
        verifyResourceSplitOrProgramSplit(featureSplit);
        if (getProgramConsumer() != null && !(getProgramConsumer() instanceof DexIndexedConsumer)) {
          reporter.error("R8 does not support class file output when using feature splits");
        }
      }

      for (Path file : programFiles) {
        if (FileUtils.isDexFile(file) && !allowDexInputToR8) {
          reporter.error(new StringDiagnostic(
              "R8 does not support compiling DEX inputs", new PathOrigin(file)));
        }
      }
      if (getProgramConsumer() instanceof ClassFileConsumer && isMinApiLevelSet()) {
        reporter.error("R8 does not support --min-api when compiling to class files");
      }
      if (hasDesugaredLibraryConfiguration() && getDisableDesugaring()) {
        reporter.error("Using desugared library configuration requires desugaring to be enabled");
      }
      buildAndValidateR8Partial();
      super.validate();
    }

    private void buildAndValidateR8Partial() {
      if (!partialCompilationConfiguration.isEnabled()
          && partialOptimizationConfigurationProviders.isEmpty()) {
        return;
      }
      Reporter reporter = getReporter();
      if (partialCompilationConfiguration.isEnabled()
          && !partialOptimizationConfigurationProviders.isEmpty()) {
        reporter.error("Cannot mix experimental partial compilation with partial optimization");
      }
      if (!partialOptimizationConfigurationProviders.isEmpty()) {
        assert !partialCompilationConfiguration.isEnabled();
        R8PartialCompilationConfiguration.Builder configurationBuilder =
            R8PartialCompilationConfiguration.builder();
        partialOptimizationConfigurationProviders.forEach(
            provider -> provider.getPartialOptimizationConfiguration(configurationBuilder));
        partialCompilationConfiguration = configurationBuilder.build();
      }
      if (!(getProgramConsumer() instanceof DexIndexedConsumer)) {
        reporter.error("Partial shrinking does not support generating class files");
      }
      if (!hasNativeMultidex()) {
        reporter.error("Partial shrinking requires min API level >= 21");
      }
      if (forceProguardCompatibility) {
        reporter.error("Partial shrinking does not support Proguard compatibility mode");
      }
      if (androidResourceProvider != null
          && !resourceShrinkerConfiguration.isOptimizedShrinking()) {
        reporter.warning(
            "Partial shrinking only supports optimized resource shrinking. "
                + "Optimized resource shrinking will be enabled implicitly.");
      }
    }

    private void validateProguardConfiguration(
        ProguardConfiguration configuration, ProguardConfigurationParserOptions parserOptions) {
      Reporter reporter = getReporter();
      if (!parserOptions.isKeepRuntimeInvisibleAnnotationsEnabled()) {
        if (configuration.getKeepAttributes().runtimeInvisibleAnnotations
            || configuration.getKeepAttributes().runtimeInvisibleParameterAnnotations
            || configuration.getKeepAttributes().runtimeInvisibleTypeAnnotations) {
          throw fatalError(
              new StringDiagnostic("Illegal attempt to keep runtime invisible annotations"));
        }
      }
      if (partialCompilationConfiguration.isEnabled()) {
        if (configuration.isProtoShrinkingEnabled()) {
          reporter.error("Partial shrinking does not support -shrinkunusedprotofields");
        }
      }
    }

    private boolean hasMainDexList() {
      return getAppBuilder().hasMainDexList();
    }

    private boolean hasMainDexRules() {
      return !mainDexRules.isEmpty();
    }

    private static void verifyResourceSplitOrProgramSplit(FeatureSplit featureSplit) {
      assert featureSplit.getProgramConsumer() instanceof DexIndexedConsumer
          || featureSplit.getAndroidResourceProvider() != null;
    }

    @Override
    R8Command makeCommand() {
      // If printing versions ignore everything else.
      if (isPrintHelp() || isPrintVersion()) {
        return new R8Command(isPrintHelp(), isPrintVersion());
      }
      DexItemFactory factory = new DexItemFactory();
      return makeR8Command(factory, makeConfiguration(factory));
    }

    R8Command makeR8Command(DexItemFactory factory, ProguardConfiguration configuration) {
      long created = System.nanoTime();
      Reporter reporter = getReporter();
      List<ProguardConfigurationRule> mainDexKeepRules =
          ProguardConfigurationParser.parseMainDex(mainDexRules, factory, reporter);

      DesugaredLibrarySpecification desugaredLibrarySpecification =
          getDesugaredLibraryConfiguration(factory, false);
      getAppBuilder().addFilteredLibraryArchives(configuration.getLibraryjars());

      assert getProgramConsumer() != null;

      DesugarState desugaring =
          (getProgramConsumer() instanceof ClassFileConsumer)
              ? DesugarState.OFF
              : getDesugaringState();

      R8Command command =
          new R8Command(
              getAppBuilder().build(),
              getProgramConsumer(),
              mainDexKeepRules,
              getMainDexListConsumer(),
              configuration,
              getMode(),
              getMinApiLevel(),
              reporter,
              desugaring,
              configuration.isShrinking(),
              configuration.isObfuscating(),
              enableVerboseSyntheticNames,
              forceProguardCompatibility,
              protectApiSurface,
              includeDataResources,
              proguardMapConsumer,
              partitionMapConsumer,
              proguardUsageConsumer,
              proguardSeedsConsumer,
              proguardConfigurationConsumer,
              keptGraphConsumer,
              mainDexKeptGraphConsumer,
              syntheticProguardRulesConsumer,
              isOptimizeMultidexForLinearAlloc(),
              getIncludeClassesChecksum(),
              getDexClassChecksumFilter(),
              desugaredLibrarySpecification,
              featureSplitConfigurationBuilder.build(factory),
              getAssertionsConfiguration(),
              getOutputInspections(),
              synthesizedClassPrefix,
              getThreadCount(),
              getDumpInputFlags(),
              getMapIdProvider(),
              getSourceFileProvider(),
              enableMissingLibraryApiModeling,
              enableStartupLayoutOptimization,
              getAndroidPlatformBuild(),
              getArtProfilesForRewriting(),
              getStartupProfileProviders(),
              getClassConflictResolver(),
              getCancelCompilationChecker(),
              androidResourceProvider,
              androidResourceConsumer,
              resourceShrinkerConfiguration,
              keepSpecifications,
              buildMetadataConsumer,
              partialCompilationConfiguration,
              created);

      if (inputDependencyGraphConsumer != null) {
        inputDependencyGraphConsumer.finished();
      }
      return command;
    }

    private ProguardConfiguration makeConfiguration(DexItemFactory factory) {
      ProguardConfigurationParserOptions parserOptions =
          parserOptionsBuilder.setForceProguardCompatibility(forceProguardCompatibility).build();
      ProguardConfiguration.Builder configurationBuilder =
          ProguardConfiguration.builder(factory, getReporter())
              .setForceProguardCompatibility(forceProguardCompatibility);
      ProguardConfigurationParser parser =
          new ProguardConfigurationParser(
              factory,
              getReporter(),
              parserOptions,
              inputDependencyGraphConsumer,
              configurationBuilder);
      if (!proguardConfigs.isEmpty()) {
        parser.parse(proguardConfigs);
      }

      if (getMode() == CompilationMode.DEBUG) {
        disableMinification = true;
        configurationBuilder.disableOptimization();
      }

      if (disableTreeShaking) {
        configurationBuilder.disableShrinking();
      }

      if (disableMinification) {
        configurationBuilder.disableObfuscation();
      }

      if (proguardConfigurationConsumerForTesting != null) {
        proguardConfigurationConsumerForTesting.accept(configurationBuilder);
      }

      // Add embedded keep rules.
      amendWithRulesAndProvidersForInjarsAndMetaInf(getReporter(), parser, configurationBuilder);

      // Extract out rules for keep annotations and amend the configuration.
      // TODO(b/248408342): Remove this and parse annotations as part of R8 root-set & enqueuer.
      extractKeepAnnotationRules(parser);
      ProguardConfiguration configuration = configurationBuilder.build();
      validateProguardConfiguration(configuration, parserOptions);
      return configuration;
    }

    private void amendWithRulesAndProvidersForInjarsAndMetaInf(
        Reporter reporter,
        ProguardConfigurationParser parser,
        ProguardConfiguration.Builder configurationBuilder) {

      Supplier<SemanticVersion> semanticVersionSupplier =
          SemanticVersionUtils.compilerVersionSemanticVersionSupplier(
              fakeCompilerVersion,
              "Using an artificial version newer than any known version for selecting"
                  + " Proguard configurations embedded under META-INF/. This means that"
                  + " all rules with a '-upto-' qualifier will be excluded and all rules"
                  + " with a -from- qualifier will be included.",
              reporter);
      Set<FilteredClassPath> seen = SetUtils.newIdentityHashSet();
      // Find resources in program providers. Both from API and added through legacy -injars in
      // configuration files.
      List<DataResourceProvider> providers =
          new ArrayList<>(
              getAppBuilder().getProgramResourceProviders().stream()
                  .map(ProgramResourceProvider::getDataResourceProvider)
                  .filter(Objects::nonNull)
                  .collect(Collectors.toList()));
      for (FilteredClassPath injar : configurationBuilder.getInjars()) {
        if (seen.add(injar)) {
          ArchiveResourceProvider provider = getAppBuilder().createAndAddProvider(injar);
          if (provider != null) {
            providers.add(provider);
          }
        }
      }

      if (readEmbeddedRulesFromClasspathAndLibrary) {
        // Find resources in classpath providers. No legacy configuration file option for classpath.
        getAppBuilder().getClasspathResourceProviders().stream()
            .map(ClassFileResourceProvider::getDataResourceProvider)
            .filter(Objects::nonNull)
            .forEach(providers::add);
        // Find resources in library providers. Both from API and added through legacy -libraryjars
        // in configuration files.
        getAppBuilder().getLibraryResourceProviders().stream()
            .map(ClassFileResourceProvider::getDataResourceProvider)
            .filter(Objects::nonNull)
            .forEach(providers::add);
        for (FilteredClassPath libraryjar : configurationBuilder.build().getLibraryjars()) {
          if (seen.add(libraryjar)) {
            ArchiveResourceProvider provider = getAppBuilder().createAndAddProvider(libraryjar);
            if (provider != null) {
              providers.add(provider);
            }
          }
        }
      }
      for (DataResourceProvider provider : providers) {
        parseEmbeddedRules(reporter, parser, semanticVersionSupplier, provider);
      }
    }

    private static void parseEmbeddedRules(
        Reporter reporter,
        ProguardConfigurationParser parser,
        Supplier<SemanticVersion> semanticVersionSupplier,
        DataResourceProvider dataResourceProvider) {
      if (dataResourceProvider != null) {
        try {
          EmbeddedRulesExtractor embeddedProguardConfigurationVisitor =
              new EmbeddedRulesExtractor(reporter, semanticVersionSupplier);
          dataResourceProvider.accept(embeddedProguardConfigurationVisitor);
          embeddedProguardConfigurationVisitor.parseRelevantRules(parser);
        } catch (ResourceException e) {
          reporter.error(new ExceptionDiagnostic(e));
        }
      }
    }

    private void extractKeepAnnotationRules(ProguardConfigurationParser parser) {
      if (!enableExperimentalKeepAnnotations) {
        return;
      }
      try {
        for (ProgramResourceProvider provider : getAppBuilder().getProgramResourceProviders()) {
          ProgramResourceProviderUtils.forEachProgramResourceCompat(
              provider,
              programResource -> {
                if (programResource.getKind() == Kind.CF) {
                  List<KeepDeclaration> declarations =
                      KeepEdgeReader.readKeepEdges(
                          ProgramResourceUtils.getBytesUnchecked(programResource));
                  if (!declarations.isEmpty()) {
                    KeepRuleExtractor extractor =
                        new KeepRuleExtractor(
                            rule -> {
                              ProguardConfigurationSourceStrings source =
                                  new ProguardConfigurationSourceStrings(
                                      rule, null, programResource.getOrigin());
                              parser.parse(source);
                            });
                    declarations.forEach(extractor::extract);
                  }
                }
              });
        }
      } catch (ResourceException e) {
        throw getAppBuilder().getReporter().fatalError(new ExceptionDiagnostic(e));
      }
    }

    // Internal for-testing method to add post-processors of the proguard configuration.
    void addProguardConfigurationConsumerForTesting(Consumer<ProguardConfiguration.Builder> c) {
      Consumer<ProguardConfiguration.Builder> oldConsumer = proguardConfigurationConsumerForTesting;
      proguardConfigurationConsumerForTesting =
          builder -> {
            if (oldConsumer != null) {
              oldConsumer.accept(builder);
            }
            c.accept(builder);
          };
    }

    void addSyntheticProguardRulesConsumerForTesting(
        Consumer<List<ProguardConfigurationRule>> consumer) {
      syntheticProguardRulesConsumer =
          syntheticProguardRulesConsumer == null
              ? consumer
              : syntheticProguardRulesConsumer.andThen(consumer);

    }

    // Internal for-testing method to allow proguard options only available for testing.
    void setEnableTestProguardOptions() {
      parserOptionsBuilder.setEnableTestingOptions(true);
    }
  }

  // Wrapper class to ensure that R8 does not allow DEX as program inputs.
  public static class EnsureNonDexProgramResourceProvider implements ProgramResourceProvider {

    final ProgramResourceProvider provider;

    public EnsureNonDexProgramResourceProvider(ProgramResourceProvider provider) {
      this.provider = provider;
    }

    @Override
    public void getProgramResources(Consumer<ProgramResource> consumer) throws ResourceException {
      Box<ProgramResource> dexResource = new Box<>();
      ProgramResourceProviderUtils.forEachProgramResourceCompat(
          provider,
          resource -> {
            if (resource.getKind() == Kind.CF) {
              consumer.accept(resource);
            } else {
              assert resource.getKind() == Kind.DEX;
              dexResource.set(resource);
            }
          });
      if (dexResource.isSet()) {
        throw new ResourceException(
            dexResource.get().getOrigin(), "R8 does not support compiling DEX inputs");
      }
    }

    @Deprecated
    @Override
    public Collection<ProgramResource> getProgramResources() throws ResourceException {
      Collection<ProgramResource> resources = provider.getProgramResources();
      for (ProgramResource resource : resources) {
        if (resource.getKind() == Kind.DEX) {
          throw new ResourceException(
              resource.getOrigin(), "R8 does not support compiling DEX inputs");
        }
      }
      return resources;
    }

    @Override
    public DataResourceProvider getDataResourceProvider() {
      return provider.getDataResourceProvider();
    }

    public static ProgramResourceProvider unwrap(ProgramResourceProvider provider) {
      if (provider instanceof EnsureNonDexProgramResourceProvider) {
        EnsureNonDexProgramResourceProvider wrapper =
            (EnsureNonDexProgramResourceProvider) provider;
        return wrapper.provider;
      }
      return provider;
    }
  }

  static String getUsageMessage() {
    return R8CommandParser.getUsageMessage();
  }

  private final List<ProguardConfigurationRule> mainDexKeepRules;
  private final ProguardConfiguration proguardConfiguration;
  private final List<KeepSpecificationSource> keepSpecifications;
  private final boolean enableTreeShaking;
  private final boolean enableMinification;
  private final boolean forceProguardCompatibility;
  private final boolean protectApiSurface;
  private final Optional<Boolean> includeDataResources;
  private final MapConsumer proguardMapConsumer;
  private final PartitionMapConsumer partitionMapConsumer;
  private final StringConsumer proguardUsageConsumer;
  private final StringConsumer proguardSeedsConsumer;
  private final StringConsumer proguardConfigurationConsumer;
  private final GraphConsumer keptGraphConsumer;
  private final GraphConsumer mainDexKeptGraphConsumer;
  private final Consumer<List<ProguardConfigurationRule>> syntheticProguardRulesConsumer;
  private final DesugaredLibrarySpecification desugaredLibrarySpecification;
  private final FeatureSplitConfiguration featureSplitConfiguration;
  private final String synthesizedClassPrefix;
  private final boolean enableMissingLibraryApiModeling;
  private final boolean enableStartupLayoutOptimization;
  private final AndroidResourceProvider androidResourceProvider;
  private final AndroidResourceConsumer androidResourceConsumer;
  private final ResourceShrinkerConfiguration resourceShrinkerConfiguration;
  private final Consumer<? super R8BuildMetadata> buildMetadataConsumer;
  private final R8PartialCompilationConfiguration partialCompilationConfiguration;
  private final long created;

  /** Get a new {@link R8Command.Builder}. */
  public static Builder builder() {
    return new Builder();
  }

  /** Get a new {@link R8Command.Builder} using a custom defined diagnostics handler. */
  public static Builder builder(DiagnosticsHandler diagnosticsHandler) {
    return new Builder(diagnosticsHandler);
  }

  // Internal builder to start from an existing AndroidApp.
  static Builder builder(AndroidApp app) {
    return new Builder(app);
  }

  // Internal builder to start from an existing AndroidApp.
  static Builder builder(AndroidApp app, DiagnosticsHandler diagnosticsHandler) {
    return new Builder(app, diagnosticsHandler);
  }

  /**
   * Parse the R8 command-line.
   *
   * Parsing will set the supplied options or their default value if they have any.
   *
   * @param args Command-line arguments array.
   * @param origin Origin description of the command-line arguments.
   * @return R8 command builder with state set up according to parsed command line.
   */
  public static Builder parse(String[] args, Origin origin) {
    return R8CommandParser.parse(args, origin);
  }

  /**
   * Parse the R8 command-line.
   *
   * Parsing will set the supplied options or their default value if they have any.
   *
   * @param args Command-line arguments array.
   * @param origin Origin description of the command-line arguments.
   * @param handler Custom defined diagnostics handler.
   * @return R8 command builder with state set up according to parsed command line.
   */
  public static Builder parse(String[] args, Origin origin, DiagnosticsHandler handler) {
    return R8CommandParser.parse(args, origin, handler);
  }

  /** Get the help description for the R8 supported flags. */
  public static List<ParseFlagInfo> getParseFlagsInformation() {
    return ImmutableList.copyOf(R8CommandParser.getFlags());
  }

  private R8Command(
      AndroidApp inputApp,
      ProgramConsumer programConsumer,
      List<ProguardConfigurationRule> mainDexKeepRules,
      StringConsumer mainDexListConsumer,
      ProguardConfiguration proguardConfiguration,
      CompilationMode mode,
      int minApiLevel,
      Reporter reporter,
      DesugarState enableDesugaring,
      boolean enableTreeShaking,
      boolean enableMinification,
      boolean enableVerboseSyntheticNames,
      boolean forceProguardCompatibility,
      boolean protectApiSurface,
      Optional<Boolean> includeDataResources,
      MapConsumer proguardMapConsumer,
      PartitionMapConsumer partitionMapConsumer,
      StringConsumer proguardUsageConsumer,
      StringConsumer proguardSeedsConsumer,
      StringConsumer proguardConfigurationConsumer,
      GraphConsumer keptGraphConsumer,
      GraphConsumer mainDexKeptGraphConsumer,
      Consumer<List<ProguardConfigurationRule>> syntheticProguardRulesConsumer,
      boolean optimizeMultidexForLinearAlloc,
      boolean encodeChecksum,
      BiPredicate<String, Long> dexClassChecksumFilter,
      DesugaredLibrarySpecification desugaredLibrarySpecification,
      FeatureSplitConfiguration featureSplitConfiguration,
      List<AssertionsConfiguration> assertionsConfiguration,
      List<Consumer<Inspector>> outputInspections,
      String synthesizedClassPrefix,
      int threadCount,
      DumpInputFlags dumpInputFlags,
      MapIdProvider mapIdProvider,
      SourceFileProvider sourceFileProvider,
      boolean enableMissingLibraryApiModeling,
      boolean enableStartupLayoutOptimization,
      boolean isAndroidPlatformBuild,
      List<ArtProfileForRewriting> artProfilesForRewriting,
      List<StartupProfileProvider> startupProfileProviders,
      ClassConflictResolver classConflictResolver,
      CancelCompilationChecker cancelCompilationChecker,
      AndroidResourceProvider androidResourceProvider,
      AndroidResourceConsumer androidResourceConsumer,
      ResourceShrinkerConfiguration resourceShrinkerConfiguration,
      List<KeepSpecificationSource> keepSpecifications,
      Consumer<? super R8BuildMetadata> buildMetadataConsumer,
      R8PartialCompilationConfiguration partialCompilationConfiguration,
      long created) {
    super(
        inputApp,
        mode,
        programConsumer,
        mainDexListConsumer,
        minApiLevel,
        reporter,
        enableDesugaring,
        optimizeMultidexForLinearAlloc,
        encodeChecksum,
        dexClassChecksumFilter,
        assertionsConfiguration,
        outputInspections,
        threadCount,
        dumpInputFlags,
        mapIdProvider,
        sourceFileProvider,
        isAndroidPlatformBuild,
        artProfilesForRewriting,
        startupProfileProviders,
        classConflictResolver,
        cancelCompilationChecker,
        enableVerboseSyntheticNames);
    assert proguardConfiguration != null;
    assert mainDexKeepRules != null;
    this.mainDexKeepRules = mainDexKeepRules;
    this.proguardConfiguration = proguardConfiguration;
    this.keepSpecifications = keepSpecifications;
    this.enableTreeShaking = enableTreeShaking;
    this.enableMinification = enableMinification;
    this.forceProguardCompatibility = forceProguardCompatibility;
    this.protectApiSurface = protectApiSurface;
    this.includeDataResources = includeDataResources;
    this.proguardMapConsumer = proguardMapConsumer;
    this.partitionMapConsumer = partitionMapConsumer;
    this.proguardUsageConsumer = proguardUsageConsumer;
    this.proguardSeedsConsumer = proguardSeedsConsumer;
    this.proguardConfigurationConsumer = proguardConfigurationConsumer;
    this.keptGraphConsumer = keptGraphConsumer;
    this.mainDexKeptGraphConsumer = mainDexKeptGraphConsumer;
    this.syntheticProguardRulesConsumer = syntheticProguardRulesConsumer;
    this.desugaredLibrarySpecification = desugaredLibrarySpecification;
    this.featureSplitConfiguration = featureSplitConfiguration;
    this.synthesizedClassPrefix = synthesizedClassPrefix;
    this.enableMissingLibraryApiModeling = enableMissingLibraryApiModeling;
    this.enableStartupLayoutOptimization = enableStartupLayoutOptimization;
    this.androidResourceProvider = androidResourceProvider;
    this.androidResourceConsumer = androidResourceConsumer;
    this.resourceShrinkerConfiguration = resourceShrinkerConfiguration;
    this.buildMetadataConsumer = buildMetadataConsumer;
    this.partialCompilationConfiguration = partialCompilationConfiguration;
    this.created = created;
  }

  private R8Command(boolean printHelp, boolean printVersion) {
    super(printHelp, printVersion);
    mainDexKeepRules = ImmutableList.of();
    proguardConfiguration = null;
    keepSpecifications = null;
    enableTreeShaking = false;
    enableMinification = false;
    forceProguardCompatibility = false;
    protectApiSurface = false;
    includeDataResources = null;
    proguardMapConsumer = null;
    partitionMapConsumer = null;
    proguardUsageConsumer = null;
    proguardSeedsConsumer = null;
    proguardConfigurationConsumer = null;
    keptGraphConsumer = null;
    mainDexKeptGraphConsumer = null;
    syntheticProguardRulesConsumer = null;
    desugaredLibrarySpecification = null;
    featureSplitConfiguration = null;
    synthesizedClassPrefix = null;
    enableMissingLibraryApiModeling = false;
    enableStartupLayoutOptimization = true;
    androidResourceProvider = null;
    androidResourceConsumer = null;
    resourceShrinkerConfiguration = null;
    buildMetadataConsumer = null;
    partialCompilationConfiguration = null;
    created = -1;
  }

  public DexItemFactory getDexItemFactory() {
    return proguardConfiguration.getDexItemFactory();
  }

  /** Get the enable-tree-shaking state. */
  public boolean getEnableTreeShaking() {
    return enableTreeShaking;
  }

  /** Get the enable-minification state. */
  public boolean getEnableMinification() {
    return enableMinification;
  }

  /** Get the Proguard compatibility state. */
  public boolean getProguardCompatibility() {
    return forceProguardCompatibility;
  }

  @Override
  InternalOptions getInternalOptions() {
    InternalOptions internal = new InternalOptions(getMode(), proguardConfiguration, getReporter());
    internal.created = created;
    assert !internal.testing.allowOutlinerInterfaceArrayArguments;  // Only allow in tests.
    internal.programConsumer = getProgramConsumer();
    internal.setMinApiLevel(AndroidApiLevel.getAndroidApiLevel(getMinApiLevel()));
    internal.desugarState = getDesugarState();
    internal.desugarSpecificOptions().enableVerboseSyntheticNames = enableVerboseSyntheticNames;
    assert internal.isShrinking() == getEnableTreeShaking();
    assert internal.isMinifying() == getEnableMinification();
    assert !internal.ignoreMissingClasses;
    internal.ignoreMissingClasses =
        proguardConfiguration.isIgnoreWarnings()
            || (forceProguardCompatibility
                && !internal.isOptimizing()
                && !internal.isShrinking()
                && !internal.isMinifying());

    internal.setKeepSpecificationSources(keepSpecifications);

    assert !internal.verbose;
    internal.mainDexKeepRules = mainDexKeepRules;
    internal.minimalMainDex = internal.debug;
    internal.mainDexListConsumer = getMainDexListConsumer();
    internal.lineNumberOptimization =
        (internal.isOptimizing() || internal.isMinifying())
            ? LineNumberOptimization.ON
            : LineNumberOptimization.OFF;
    MappingComposeOptions mappingComposeOptions = internal.mappingComposeOptions();
    mappingComposeOptions.enableExperimentalMappingComposition = true;

    HorizontalClassMergerOptions horizontalClassMergerOptions =
        internal.horizontalClassMergerOptions();
    assert internal.isOptimizing() || horizontalClassMergerOptions.isRestrictedToSynthetics();

    assert !internal.enableTreeShakingOfLibraryMethodOverrides;

    if (!internal.isShrinking()) {
      // If R8 is not shrinking, there is no point in running various optimizations since the
      // optimized classes will still remain in the program (the application size could increase).
      internal.enableEnumUnboxing = false;
    }

    // Amend the proguard-map consumer with options from the proguard configuration.
    MapConsumer mapConsumer =
        wrapMapConsumer(
            proguardMapConsumer,
            proguardConfiguration.isPrintMapping(),
            proguardConfiguration.getPrintMappingFile());
    InternalMapConsumer internalMapConsumer =
        wrapExistingInternalMapConsumerIfNotNull(
            internal.mapConsumer, partitionMapConsumer, MapConsumerToPartitionMapConsumer::new);
    internalMapConsumer =
        wrapExistingInternalMapConsumerIfNotNull(
            internalMapConsumer,
            mapConsumer,
            nonNullStringConsumer ->
                InternalMapConsumerImpl.builder().setMapConsumer(mapConsumer).build());

    internal.mapConsumer =
        wrapExistingInternalMapConsumerIfNotNull(
            internalMapConsumer,
            androidResourceConsumer,
            nonNulStringConsumer -> new ResourceShrinkerMapStringConsumer(internal));
    // Amend the usage information consumer with options from the proguard configuration.
    internal.usageInformationConsumer =
        wrapStringConsumer(
            proguardUsageConsumer,
            proguardConfiguration.isPrintUsage(),
            proguardConfiguration.getPrintUsageFile());

    // Amend the pg-seeds consumer with options from the proguard configuration.
    internal.proguardSeedsConsumer =
        wrapStringConsumer(
            proguardSeedsConsumer,
            proguardConfiguration.isPrintSeeds(),
            proguardConfiguration.getSeedFile());

    // Amend the configuration consumer with options from the proguard configuration.
    internal.configurationConsumer =
        wrapStringConsumer(
            proguardConfigurationConsumer,
            proguardConfiguration.isPrintConfiguration(),
            proguardConfiguration.getPrintConfigurationFile());

    // Set the kept-graph consumer if any. It will only be actively used if the enqueuer triggers.
    internal.keptGraphConsumer = keptGraphConsumer;
    internal.mainDexKeptGraphConsumer = mainDexKeptGraphConsumer;

    internal.r8BuildMetadataConsumer = buildMetadataConsumer;
    internal.dataResourceConsumer = internal.programConsumer.getDataResourceConsumer();

    internal.setFeatureSplitConfiguration(featureSplitConfiguration);

    internal.syntheticProguardRulesConsumer = syntheticProguardRulesConsumer;

    internal.outputInspections = InspectorImpl.wrapInspections(getOutputInspections());

    if (!enableMissingLibraryApiModeling) {
      internal.apiModelingOptions().disableApiModeling();
    }

    // Default is to remove all javac generated assertion code when generating DEX.
    assert internal.assertionsConfiguration == null;
    AssertionsConfiguration.Builder builder = AssertionsConfiguration.builder(getReporter());
    internal.assertionsConfiguration =
        new AssertionConfigurationWithDefault(
            getProgramConsumer() instanceof ClassFileConsumer
                ? AssertionsConfiguration.Builder.passthroughAllAssertions(builder)
                : AssertionsConfiguration.Builder.compileTimeDisableAllAssertions(builder),
            getAssertionsConfiguration());

    // TODO(b/171552739): Enable class merging for CF. When compiling libraries, we need to be
    //  careful when merging a public member 'm' from a class A into another class B, since B could
    //  have a kept subclass, in which case 'm' would leak into the public API.
    if (internal.isGeneratingClassFiles()) {
      horizontalClassMergerOptions.disable();
      // R8 CF output does not support desugaring so disable it.
      internal.desugarState = DesugarState.OFF;
      // TODO(b/333477035): Since D8 dexing now supports outline/stubbing API calls R8/CF should
      //  likely disable API caller identification too so as not to prevent inlining.
      internal.apiModelingOptions().disableOutlining().disableStubbingOfClasses();
    }

    // EXPERIMENTAL flags.
    assert partialCompilationConfiguration != null;
    internal.partialCompilationConfiguration = partialCompilationConfiguration;

    assert !internal.forceProguardCompatibility;
    internal.forceProguardCompatibility = forceProguardCompatibility;
    internal.protectApiSurface = protectApiSurface;

    internal.enableInheritanceClassInDexDistributor = isOptimizeMultidexForLinearAlloc();

    LibraryDesugaringOptions libraryDesugaringOptions = internal.getLibraryDesugaringOptions();
    libraryDesugaringOptions.configureDesugaredLibrary(
        desugaredLibrarySpecification, synthesizedClassPrefix);
    boolean l8Shrinking = libraryDesugaringOptions.isL8();
    // TODO(b/214382176): Enable all the time.
    internal.loadAllClassDefinitions = l8Shrinking;
    if (l8Shrinking) {
      internal.apiModelingOptions().disableStubbingOfClasses();
      internal.ignoreUnusedProguardRules = true;
    }

    // Set up the map and source file providers.
    // Note that minify/optimize settings must be set on internal options before doing this.
    internal.mapIdProvider = getMapIdProvider();
    internal.sourceFileProvider =
        SourceFileRewriter.computeSourceFileProvider(getSourceFileProvider(), internal);

    internal.configureAndroidPlatformBuild(getAndroidPlatformBuild());

    internal.getArtProfileOptions().setArtProfilesForRewriting(getArtProfilesForRewriting());
    if (!getStartupProfileProviders().isEmpty()) {
      internal
          .getStartupOptions()
          .setStartupProfileProviders(getStartupProfileProviders())
          .setEnableStartupLayoutOptimization(enableStartupLayoutOptimization);
    }

    internal.programClassConflictResolver =
        ProgramClassCollection.wrappedConflictResolver(
            getClassConflictResolver(), internal.reporter);

    internal.cancelCompilationChecker = getCancelCompilationChecker();

    internal.androidResourceProvider = androidResourceProvider;
    internal.androidResourceConsumer = androidResourceConsumer;
    internal.resourceShrinkerConfiguration = resourceShrinkerConfiguration;

    if (!DETERMINISTIC_DEBUGGING) {
      assert internal.threadCount == ThreadUtils.NOT_SPECIFIED;
      internal.threadCount = getThreadCount();
    }

    internal.tool = Tool.R8;

    internal.setDumpInputFlags(getDumpInputFlags());
    internal.dumpOptions = dumpOptions();

    return internal;
  }

  private static MapConsumer wrapMapConsumer(
      MapConsumer consumer, boolean optionsFlag, Path optionFile) {
    if (optionsFlag) {
      if (optionFile != null) {
        return new MapConsumer.FileConsumer(optionFile, consumer);
      } else {
        return MapConsumerUtils.createStandardOutConsumer(consumer);
      }
    }
    return consumer;
  }

  private static StringConsumer wrapStringConsumer(
      StringConsumer optionConsumer, boolean optionsFlag, Path optionFile) {
    if (optionsFlag) {
      if (optionFile != null) {
        return new StringConsumer.FileConsumer(optionFile, optionConsumer);
      } else {
        return new StandardOutConsumer(optionConsumer);
      }
    }
    return optionConsumer;
  }

  private static class ResourceShrinkerMapStringConsumer implements InternalMapConsumer {

    private final InternalOptions internal;
    private final Predicate<String> classNamePredicate;
    private StringBuilder resultBuilder = new StringBuilder();

    public ResourceShrinkerMapStringConsumer(InternalOptions internal) {
      this.internal = internal;
      this.classNamePredicate =
          LegacyResourceShrinker.classNamesNeededForResourceShrinkingPredicate();
    }

    @Override
    public void finished(DiagnosticsHandler handler) {
      internal.androidResourceProguardMapStrings = StringUtils.splitLines(resultBuilder.toString());
      resultBuilder = null;
    }

    @Override
    public void accept(DiagnosticsHandler diagnosticsHandler, ClassNameMapper classNameMapper) {
      for (ClassNamingForNameMapper entry : classNameMapper.getClassNameMappings().values()) {
        if (classNamePredicate.test(entry.originalName)) {
          resultBuilder.append(entry.toProguardMapSource());
        }
      }
    }
  }

  private static class StandardOutConsumer extends StringConsumer.ForwardingConsumer {

    public StandardOutConsumer(StringConsumer consumer) {
      super(consumer);
    }

    @Override
    public void accept(String string, DiagnosticsHandler handler) {
      super.accept(string, handler);
      System.out.print(string);
    }
  }

  private DumpOptions dumpOptions() {
    DumpOptions.Builder builder = DumpOptions.builder(Tool.R8).readCurrentSystemProperties();
    dumpBaseCommandOptions(builder);
    return builder
        .setIncludeDataResources(includeDataResources)
        .setTreeShaking(getEnableTreeShaking())
        .setMinification(getEnableMinification())
        .setForceProguardCompatibility(forceProguardCompatibility)
        .setFeatureSplitConfiguration(featureSplitConfiguration)
        .setAndroidResourceProvider(androidResourceProvider)
        .setPartialCompilationConfiguration(partialCompilationConfiguration)
        .setProguardConfiguration(proguardConfiguration)
        .setMainDexKeepRules(mainDexKeepRules)
        .setDesugaredLibraryConfiguration(desugaredLibrarySpecification)
        .setEnableMissingLibraryApiModeling(enableMissingLibraryApiModeling)
        .applyIf(
            featureSplitConfiguration != null,
            b -> b.setIsolatedSplits(featureSplitConfiguration.isIsolatedSplitsEnabled()))
        .applyIf(
            resourceShrinkerConfiguration != null,
            b ->
                b.setOptimizedResourceShrinking(
                    resourceShrinkerConfiguration.isOptimizedShrinking()))
        .build();
  }

}
