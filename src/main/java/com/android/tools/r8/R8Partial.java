// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.diagnostic.R8VersionDiagnostic;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.features.FeatureSplitConfiguration;
import com.android.tools.r8.graph.DexClasspathClass;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.graph.LazyLoadedDexApplication;
import com.android.tools.r8.keepanno.ast.KeepDeclaration;
import com.android.tools.r8.partial.R8PartialCompilationConfiguration;
import com.android.tools.r8.partial.R8PartialD8Input;
import com.android.tools.r8.partial.R8PartialD8Result;
import com.android.tools.r8.partial.R8PartialProgramPartitioning;
import com.android.tools.r8.partial.R8PartialSubCompilationConfiguration.R8PartialD8SubCompilationConfiguration;
import com.android.tools.r8.partial.R8PartialSubCompilationConfiguration.R8PartialR8SubCompilationConfiguration;
import com.android.tools.r8.profile.art.ArtProfileOptions;
import com.android.tools.r8.profile.startup.StartupOptions;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.FeatureSplitConsumers;
import com.android.tools.r8.utils.ForwardingDiagnosticsHandler;
import com.android.tools.r8.utils.InternalClasspathOrLibraryClassProvider;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.InternalProgramClassProvider;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.timing.Timing;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

class R8Partial {

  private final InternalOptions options;
  private final Timing timing;

  R8Partial(InternalOptions options) {
    this.options = options;
    this.timing = Timing.create("R8 partial " + Version.LABEL, options);
  }

  static void runForTesting(AndroidApp app, InternalOptions options)
      throws CompilationFailedException {
    ExecutorService executor = ThreadUtils.getExecutorService(options);
    ExceptionUtils.withR8CompilationHandler(
        options.reporter,
        () -> {
          try {
            new R8Partial(options).runInternal(app, executor);
          } finally {
            executor.shutdown();
          }
        });
  }

  void runInternal(AndroidApp app, ExecutorService executor) throws IOException, ResourceException {
    if (options.getProguardConfiguration().isProtoShrinkingEnabled()) {
      throw options.reporter.fatalError("Partial shrinking does not support proto shrinking");
    }

    Map<FeatureSplit, FeatureSplitConsumers> featureSplitConsumers =
        getAndClearFeatureSplitConsumers();

    timing.begin("Process input");
    R8PartialD8Input input = runProcessInputStep(app, executor);

    timing.end().begin("Run D8");
    R8PartialD8Result d8Result = runD8Step(input, executor);
    timing.end();

    setFeatureSplitConsumers(featureSplitConsumers);
    lockFeatureSplitProgramResourceProviders();

    timing.begin("Run R8");
    runR8Step(app, d8Result, executor);
    timing.end();

    if (options.isPrintTimesReportingEnabled()) {
      timing.report();
    }
  }

  private R8PartialD8Input runProcessInputStep(AndroidApp androidApp, ExecutorService executor)
      throws IOException {
    LazyLoadedDexApplication lazyApp =
        new ApplicationReader(androidApp, options, timing).read(executor);
    List<KeepDeclaration> keepDeclarations = lazyApp.getKeepDeclarations();
    DirectMappedDexApplication app = lazyApp.toDirect();
    R8PartialProgramPartitioning partitioning = R8PartialProgramPartitioning.create(app);
    partitioning.printForTesting(options);
    options.getLibraryDesugaringOptions().loadMachineDesugaredLibrarySpecification(timing, app);
    return new R8PartialD8Input(
        partitioning.getD8Classes(),
        partitioning.getR8Classes(),
        app.classpathClasses(),
        app.libraryClasses(),
        app.getFlags(),
        keepDeclarations);
  }

  private R8PartialD8Result runD8Step(R8PartialD8Input input, ExecutorService executor)
      throws IOException {
    // TODO(b/389039057): This will desugar the entire R8 part. For build speed, look into if some
    //  desugarings can be postponed to the R8 compilation, since we do not desugar dead code in R8.
    //  As a simple example, it should be safe to postpone backporting to the R8 compilation.
    D8Command.Builder d8Builder =
        D8Command.builder(options.reporter)
            .setEnableExperimentalMissingLibraryApiModeling(
                options.apiModelingOptions().isApiModelingEnabled())
            .setMinApiLevel(options.getMinApiLevel().getLevel())
            .setMode(options.getCompilationMode())
            .setProgramConsumer(DexIndexedConsumer.emptyConsumer());
    input.configure(d8Builder);
    d8Builder.validate();
    D8Command d8Command = d8Builder.makeD8Command(options.dexItemFactory());
    AndroidApp d8App = d8Command.getInputApp();
    InternalOptions d8Options = d8Command.getInternalOptions();
    forwardOptions(d8Options);
    options.partialCompilationConfiguration.d8DexOptionsConsumer.accept(d8Options);
    R8PartialD8SubCompilationConfiguration subCompilationConfiguration =
        new R8PartialD8SubCompilationConfiguration(
            input.getD8Types(),
            input.getR8Types(),
            input.getFlags(),
            options.getLibraryDesugaringOptions(),
            timing);
    d8Options.setArtProfileOptions(
        new ArtProfileOptions(d8Options, options.getArtProfileOptions()));
    d8Options.setFeatureSplitConfiguration(options.getFeatureSplitConfiguration());
    d8Options.setStartupOptions(new StartupOptions(d8Options, options.getStartupOptions()));
    d8Options.partialSubCompilationConfiguration = subCompilationConfiguration;
    D8.runInternal(d8App, d8Options, executor);
    return new R8PartialD8Result(
        subCompilationConfiguration.getArtProfiles(),
        subCompilationConfiguration.getClassToFeatureSplitMap(),
        subCompilationConfiguration.getDexedOutputClasses(),
        subCompilationConfiguration.getDesugaredOutputClasses(),
        input.getFlags(),
        input.getKeepDeclarations(),
        subCompilationConfiguration.getOutputClasspathClasses(),
        subCompilationConfiguration.getOutputLibraryClasses(),
        subCompilationConfiguration.getStartupProfile());
  }

  private void runR8Step(AndroidApp app, R8PartialD8Result d8Result, ExecutorService executor)
      throws IOException {
    // Compile R8 input with R8 using the keep rules from trace references.
    DiagnosticsHandler r8DiagnosticsHandler =
        new ForwardingDiagnosticsHandler(options.reporter) {

          @Override
          public DiagnosticsLevel modifyDiagnosticsLevel(
              DiagnosticsLevel level, Diagnostic diagnostic) {
            if (diagnostic instanceof R8VersionDiagnostic) {
              return DiagnosticsLevel.NONE;
            }
            return super.modifyDiagnosticsLevel(level, diagnostic);
          }
        };
    // TODO(b/390389764): Disable desugaring.
    R8Command.Builder r8Builder =
        R8Command.builder(r8DiagnosticsHandler)
            .addProgramResourceProvider(
                new InternalProgramClassProvider(d8Result.getDesugaredClasses()))
            .addClasspathResourceProvider(
                new InternalClasspathOrLibraryClassProvider<>(
                    DexClasspathClass.toClasspathClasses(d8Result.getDexedClasses())))
            .addClasspathResourceProvider(
                new InternalClasspathOrLibraryClassProvider<>(d8Result.getOutputClasspathClasses()))
            .addLibraryResourceProvider(
                new InternalClasspathOrLibraryClassProvider<>(d8Result.getOutputLibraryClasses()))
            .enableLegacyFullModeForKeepRules(true)
            .setEnableExperimentalMissingLibraryApiModeling(
                options.apiModelingOptions().isApiModelingEnabled())
            .setMinApiLevel(options.getMinApiLevel().getLevel())
            .setMode(options.getCompilationMode())
            .setPartialCompilationConfiguration(
                R8PartialCompilationConfiguration.disabledConfiguration())
            .setProgramConsumer(options.programConsumer);
    // The program input that R8 must compile is provided above using an
    // InternalProgramClassProvider. This passes in the data resources that we must either rewrite
    // or pass through.
    for (ProgramResourceProvider programResourceProvider : app.getProgramResourceProviders()) {
      if (programResourceProvider.getDataResourceProvider() == null) {
        programResourceProvider.finished(options.reporter);
      } else {
        r8Builder.addProgramResourceProvider(
            new ProgramResourceProvider() {

              @Override
              public Collection<ProgramResource> getProgramResources() {
                return Collections.emptyList();
              }

              @Override
              public DataResourceProvider getDataResourceProvider() {
                return programResourceProvider.getDataResourceProvider();
              }

              @Override
              public void finished(DiagnosticsHandler handler) throws IOException {
                programResourceProvider.finished(handler);
              }
            });
      }
    }
    r8Builder.validate();
    R8Command r8Command =
        r8Builder.makeR8Command(options.dexItemFactory(), options.getProguardConfiguration());
    AndroidApp r8App = r8Command.getInputApp();
    InternalOptions r8Options = r8Command.getInternalOptions();
    forwardOptions(r8Options);
    options.partialCompilationConfiguration.r8OptionsConsumer.accept(r8Options);
    r8Options.partialSubCompilationConfiguration =
        new R8PartialR8SubCompilationConfiguration(
            d8Result.getArtProfiles(),
            d8Result.getClassToFeatureSplitMap(),
            d8Result.getDexedClasses(),
            d8Result.getFlags(),
            d8Result.getKeepDeclarations(),
            d8Result.getStartupProfile(),
            timing);
    r8Options.setArtProfileOptions(
        new ArtProfileOptions(r8Options, options.getArtProfileOptions()));
    r8Options.setFeatureSplitConfiguration(options.getFeatureSplitConfiguration());
    r8Options.setStartupOptions(new StartupOptions(r8Options, options.getStartupOptions()));
    r8Options.setKeepSpecificationSources(options.getKeepSpecifications());
    r8Options.getTestingOptions().enableEmbeddedKeepAnnotations =
        options.getTestingOptions().enableEmbeddedKeepAnnotations;
    r8Options.mapConsumer = options.mapConsumer;
    r8Options.sourceFileProvider = options.sourceFileProvider;
    r8Options
        .getLibraryDesugaringOptions()
        .setMachineDesugaredLibrarySpecification(
            options.getLibraryDesugaringOptions().getMachineDesugaredLibrarySpecification())
        .setTypeRewriter(options.getLibraryDesugaringOptions().getTypeRewriter());
    if (options.androidResourceProvider != null) {
      r8Options.androidResourceProvider = options.androidResourceProvider;
      r8Options.androidResourceConsumer = options.androidResourceConsumer;
      r8Options.resourceShrinkerConfiguration = options.resourceShrinkerConfiguration;
    }
    R8.runInternal(r8App, r8Options, executor);
  }

  private Map<FeatureSplit, FeatureSplitConsumers> getAndClearFeatureSplitConsumers() {
    FeatureSplitConfiguration featureSplitConfiguration = options.getFeatureSplitConfiguration();
    if (featureSplitConfiguration == null) {
      return null;
    }
    Map<FeatureSplit, FeatureSplitConsumers> featureSplitConsumers = new IdentityHashMap<>();
    for (FeatureSplit featureSplit : featureSplitConfiguration.getFeatureSplits()) {
      featureSplitConsumers.put(featureSplit, featureSplit.internalClearConsumers());
    }
    return featureSplitConsumers;
  }

  private void setFeatureSplitConsumers(
      Map<FeatureSplit, FeatureSplitConsumers> featureSplitConsumers) {
    if (featureSplitConsumers == null) {
      return;
    }
    FeatureSplitConfiguration featureSplitConfiguration = options.getFeatureSplitConfiguration();
    for (FeatureSplit featureSplit : featureSplitConfiguration.getFeatureSplits()) {
      featureSplit.internalSetConsumers(featureSplitConsumers.get(featureSplit));
    }
    featureSplitConsumers.clear();
  }

  private void lockFeatureSplitProgramResourceProviders() {
    FeatureSplitConfiguration featureSplitConfiguration = options.getFeatureSplitConfiguration();
    if (featureSplitConfiguration == null) {
      return;
    }
    for (FeatureSplit featureSplit : featureSplitConfiguration.getFeatureSplits()) {
      List<ProgramResourceProvider> programResourceProviders =
          featureSplit.getProgramResourceProviders();
      List<ProgramResourceProvider> replacementProgramResourceProviders =
          ListUtils.map(
              programResourceProviders,
              programResourceProvider ->
                  new ProgramResourceProvider() {

                    @Override
                    public Collection<ProgramResource> getProgramResources() {
                      throw new Unreachable();
                    }

                    @Override
                    public DataResourceProvider getDataResourceProvider() {
                      return programResourceProvider.getDataResourceProvider();
                    }

                    @Override
                    public void finished(DiagnosticsHandler handler) throws IOException {
                      programResourceProvider.finished(handler);
                    }
                  });
      featureSplit.internalSetProgramResourceProviders(replacementProgramResourceProviders);
    }
  }

  private void forwardOptions(InternalOptions subCompilationOptions) {
    subCompilationOptions.emitNestAnnotationsInDex = options.emitNestAnnotationsInDex;
    subCompilationOptions.emitRecordAnnotationsInDex = options.emitRecordAnnotationsInDex;
    subCompilationOptions.emitPermittedSubclassesAnnotationsInDex =
        options.emitPermittedSubclassesAnnotationsInDex;
    subCompilationOptions.desugarState = options.desugarState;
    subCompilationOptions.forceNestDesugaring = options.forceNestDesugaring;
    subCompilationOptions.getTestingOptions().forceDexContainerFormat =
        options.getTestingOptions().forceDexContainerFormat;
  }
}
