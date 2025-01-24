// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.diagnostic.R8VersionDiagnostic;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.partial.R8PartialD8DexResult;
import com.android.tools.r8.partial.R8PartialDesugarResult;
import com.android.tools.r8.partial.R8PartialInput;
import com.android.tools.r8.partial.R8PartialProgramPartioning;
import com.android.tools.r8.partial.R8PartialSubCompilationConfiguration.R8PartialD8DesugarSubCompilationConfiguration;
import com.android.tools.r8.partial.R8PartialSubCompilationConfiguration.R8PartialD8DexSubCompilationConfiguration;
import com.android.tools.r8.partial.R8PartialSubCompilationConfiguration.R8PartialR8SubCompilationConfiguration;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.ForwardingDiagnosticsHandler;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.InternalProgramClassProvider;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

class R8Partial {

  private final InternalOptions options;
  private final Consumer<AndroidApp> r8InputAppConsumer;
  private final Consumer<AndroidApp> d8InputAppConsumer;
  private final Timing timing;

  R8Partial(InternalOptions options) {
    this.options = options;
    this.r8InputAppConsumer = options.partialCompilationConfiguration.r8InputAppConsumer;
    this.d8InputAppConsumer = options.partialCompilationConfiguration.d8InputAppConsumer;
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
    if (!options.getArtProfileOptions().getArtProfileProviders().isEmpty()) {
      throw options.reporter.fatalError(
          "Partial shrinking does not support baseline profile rewriting");
    }
    if (!options.getStartupOptions().getStartupProfileProviders().isEmpty()) {
      throw options.reporter.fatalError("Partial shrinking does not support startup profiles");
    }

    timing.begin("Process input");
    R8PartialInput input = runProcessInputStep(app, executor);

    timing.end().begin("Run dexing");
    R8PartialD8DexResult dexingResult = runDexingStep(input, executor);

    timing.end().begin("Run desugaring");
    R8PartialDesugarResult desugarResult = runDesugarStep(input, executor);

    timing.end().begin("Run R8");
    runR8PartialStep(app, input, dexingResult, desugarResult, executor);
    timing.end();

    if (options.isPrintTimesReportingEnabled()) {
      timing.report();
    }
  }

  private R8PartialInput runProcessInputStep(AndroidApp androidApp, ExecutorService executor)
      throws IOException {
    // TODO(b/388421578): Add support for generating R8 partial compile dumps.
    DirectMappedDexApplication app =
        new ApplicationReader(androidApp, options, timing).readWithoutDumping(executor).toDirect();
    R8PartialProgramPartioning partioning = R8PartialProgramPartioning.create(app);
    return new R8PartialInput(
        partioning.getD8Classes(),
        partioning.getR8Classes(),
        app.classpathClasses(),
        app.libraryClasses());
  }

  private R8PartialD8DexResult runDexingStep(R8PartialInput input, ExecutorService executor)
      throws IOException {
    D8Command.Builder d8Builder =
        D8Command.builder(options.reporter)
            .setMinApiLevel(options.getMinApiLevel().getLevel())
            .setMode(options.getCompilationMode())
            .setProgramConsumer(DexIndexedConsumer.emptyConsumer());
    // TODO(b/391572031): Configure library desugaring.
    input.configure(d8Builder);
    d8Builder.validate();
    D8Command d8Command = d8Builder.makeD8Command(options.dexItemFactory());
    AndroidApp d8App = d8Command.getInputApp();
    if (d8InputAppConsumer != null) {
      d8InputAppConsumer.accept(d8App);
    }
    InternalOptions d8Options = d8Command.getInternalOptions();
    options.partialCompilationConfiguration.d8DexOptionsConsumer.accept(d8Options);
    R8PartialD8DexSubCompilationConfiguration subCompilationConfiguration =
        new R8PartialD8DexSubCompilationConfiguration(timing);
    d8Options.partialSubCompilationConfiguration = subCompilationConfiguration;
    D8.runInternal(d8App, d8Options, executor);
    return new R8PartialD8DexResult(subCompilationConfiguration.getOutputClasses());
  }

  private R8PartialDesugarResult runDesugarStep(R8PartialInput input, ExecutorService executor)
      throws IOException {
    // TODO(b/389575762): Consume the DexProgramClasses instead of writing to a zip.
    // TODO(b/389039057): This will desugar the entire R8 part. For build speed, look into if some
    //  desugarings can be postponed to the R8 compilation, since we do not desugar dead code in R8.
    //  As a simple example, it should be safe to postpone backporting to the R8 compilation.
    // TODO(b/389039057): This runs a full D8 compilation. For build speed, consider if the various
    //  passes in D8 can be disabled when the `partialSubCompilationConfiguration` is set.
    D8Command.Builder d8Builder =
        D8Command.builder(options.reporter)
            .setMinApiLevel(options.getMinApiLevel().getLevel())
            .setMode(options.getCompilationMode())
            .setProgramConsumer(ClassFileConsumer.emptyConsumer());
    // TODO(b/390327883): This should enable intermediate mode.
    input.configureDesugar(d8Builder);
    d8Builder.validate();
    D8Command d8Command = d8Builder.makeD8Command(options.dexItemFactory());
    AndroidApp d8App = d8Command.getInputApp();
    InternalOptions d8Options = d8Command.getInternalOptions();
    options.partialCompilationConfiguration.d8DesugarOptionsConsumer.accept(d8Options);
    R8PartialD8DesugarSubCompilationConfiguration subCompilationConfiguration =
        new R8PartialD8DesugarSubCompilationConfiguration(timing);
    d8Options.partialSubCompilationConfiguration = subCompilationConfiguration;
    D8.runInternal(d8App, d8Options, executor);
    return new R8PartialDesugarResult(subCompilationConfiguration.getOutputClasses());
  }

  private void runR8PartialStep(
      AndroidApp app,
      R8PartialInput input,
      R8PartialD8DexResult dexingResult,
      R8PartialDesugarResult desugarResult,
      ExecutorService executor)
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
                new InternalProgramClassProvider(desugarResult.getOutputClasses()))
            .enableLegacyFullModeForKeepRules(true)
            .setMinApiLevel(options.getMinApiLevel().getLevel())
            .setMode(options.getCompilationMode())
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
    input.configure(r8Builder);
    r8Builder.validate();
    // TODO(b/391572031): Configure library desugaring.
    R8Command r8Command =
        r8Builder.makeR8Command(options.dexItemFactory(), options.getProguardConfiguration());
    AndroidApp r8App = r8Command.getInputApp();
    if (r8InputAppConsumer != null) {
      r8InputAppConsumer.accept(r8App);
    }
    InternalOptions r8Options = r8Command.getInternalOptions();
    options.partialCompilationConfiguration.r8OptionsConsumer.accept(r8Options);
    r8Options.partialSubCompilationConfiguration =
        new R8PartialR8SubCompilationConfiguration(dexingResult.getOutputClasses(), timing);
    r8Options.mapConsumer = options.mapConsumer;
    if (options.androidResourceProvider != null) {
      r8Options.androidResourceProvider = options.androidResourceProvider;
      r8Options.androidResourceConsumer = options.androidResourceConsumer;
      r8Options.resourceShrinkerConfiguration = options.resourceShrinkerConfiguration;
    }
    R8.runInternal(r8App, r8Options, executor);
  }
}
