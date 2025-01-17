// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.DexIndexedConsumer.ArchiveConsumer;
import com.android.tools.r8.DexIndexedConsumer.ForwardingConsumer;
import com.android.tools.r8.StringConsumer.FileConsumer;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.diagnostic.R8VersionDiagnostic;
import com.android.tools.r8.dump.CompilerDump;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.partial.R8PartialD8DexResult;
import com.android.tools.r8.partial.R8PartialDataResourceConsumer;
import com.android.tools.r8.partial.R8PartialDesugarResult;
import com.android.tools.r8.partial.R8PartialInput;
import com.android.tools.r8.partial.R8PartialInputToDumpFlags;
import com.android.tools.r8.partial.R8PartialR8Result;
import com.android.tools.r8.partial.R8PartialSubCompilationConfiguration.R8PartialD8SubCompilationConfiguration;
import com.android.tools.r8.partial.R8PartialSubCompilationConfiguration.R8PartialR8SubCompilationConfiguration;
import com.android.tools.r8.partial.R8PartialTraceReferencesResult;
import com.android.tools.r8.partial.R8PartialTraceResourcesResult;
import com.android.tools.r8.partial.ResourceTracingCallback;
import com.android.tools.r8.synthesis.SyntheticItems.GlobalSyntheticsStrategy;
import com.android.tools.r8.tracereferences.TraceReferencesBridge;
import com.android.tools.r8.tracereferences.TraceReferencesCommand;
import com.android.tools.r8.tracereferences.TraceReferencesKeepRules;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.AndroidAppConsumers;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.ForwardingDiagnosticsHandler;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import com.google.common.io.ByteStreams;
import it.unimi.dsi.fastutil.ints.IntSets;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.stream.Collectors;

class R8Partial {

  private final InternalOptions options;
  private final Consumer<AndroidApp> r8InputAppConsumer;
  private final Consumer<AndroidApp> d8InputAppConsumer;
  private final Consumer<AndroidApp> r8OutputAppConsumer;
  private final Consumer<AndroidApp> d8OutputAppConsumer;
  private final Timing timing;

  R8Partial(InternalOptions options) {
    this.options = options;
    this.r8InputAppConsumer = options.partialCompilationConfiguration.r8InputAppConsumer;
    this.d8InputAppConsumer = options.partialCompilationConfiguration.d8InputAppConsumer;
    this.r8OutputAppConsumer = options.partialCompilationConfiguration.r8OutputAppConsumer;
    this.d8OutputAppConsumer = options.partialCompilationConfiguration.d8OutputAppConsumer;
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
    if (!(options.programConsumer instanceof DexIndexedConsumer)) {
      throw options.reporter.fatalError(
          "Partial shrinking does not support generating class files");
    }

    timing.begin("Process input");
    R8PartialInput input = runProcessInputStep(app);

    timing.end().begin("Run D8 dexing");
    R8PartialD8DexResult d8DexResult = runD8DexStep(input, executor);

    timing.end().begin("Run trace resources");
    R8PartialTraceResourcesResult traceResourcesResult = runTraceResourcesStep(d8DexResult);

    timing.end().begin("Run D8 desugaring");
    R8PartialDesugarResult desugarResult = runDesugarStep(input, executor);

    timing.end().begin("Run trace references");
    R8PartialTraceReferencesResult traceReferencesResult =
        runTraceReferencesStep(input, desugarResult);

    timing.end().begin("Run R8");
    R8PartialR8Result r8Result =
        runR8PartialStep(
            input, desugarResult, traceReferencesResult, traceResourcesResult, executor);

    timing.end().begin("Run D8 merging");
    runD8MergeStep(input, d8DexResult, r8Result, executor);

    // Feed the data resource output by R8 to the output consumer. Keeping this at the end after the
    // merge keeps the order of calls to the output consumer closer to full R8.
    timing.end().begin("Supply consumers");
    r8Result.supplyConsumers(options);
    timing.end();

    if (options.isPrintTimesReportingEnabled()) {
      timing.report();
    }
  }

  private R8PartialTraceResourcesResult runTraceResourcesStep(R8PartialD8DexResult d8DexResult)
      throws IOException {
    if (options.androidResourceProvider == null) {
      return new R8PartialTraceResourcesResult(IntSets.EMPTY_SET);
    }
    // TODO(b/390135529): Consider tracing these in the enqueuer of R8.
    ResourceTracingCallback resourceTracingCallback = new ResourceTracingCallback();
    AndroidApp app = AndroidApp.builder().addProgramFile(d8DexResult.getOutputPath()).build();
    try {
      ResourceShrinker.runForTesting(app, options, resourceTracingCallback);
    } catch (ExecutionException e) {
      throw options.reporter.fatalError(new ExceptionDiagnostic(e));
    }
    return new R8PartialTraceResourcesResult(resourceTracingCallback.getPotentialIds());
  }

  private R8PartialInput runProcessInputStep(AndroidApp androidApp) throws IOException {
    // Create a dump of the compiler input.
    // TODO(b/309743298): Do not use compiler dump to handle splitting the compilation. This should
    //  be all in memory.
    ApplicationReader applicationReader = new ApplicationReader(androidApp, options, timing);
    Path dumpFile = resolveTmp("dump.zip");
    applicationReader.dump(new R8PartialInputToDumpFlags(dumpFile));
    CompilerDump dump = CompilerDump.fromArchive(dumpFile);
    if (dump.getBuildProperties().hasMainDexKeepRules()
        || dump.getBuildProperties().hasArtProfileProviders()
        || dump.getBuildProperties().hasStartupProfileProviders()) {
      throw options.reporter.fatalError(
          "Partial shrinking does not support legacy multi-dex, baseline or startup profiles");
    }

    DexApplication app = applicationReader.read().toDirect();
    AppInfoWithClassHierarchy appInfo =
        AppInfoWithClassHierarchy.createForDesugaring(
            AppInfo.createInitialAppInfo(app, GlobalSyntheticsStrategy.forNonSynthesizing()));

    Set<DexProgramClass> d8classes = new HashSet<>();
    for (DexProgramClass clazz : appInfo.classes()) {
      if (!d8classes.contains(clazz) && !options.partialCompilationConfiguration.test(clazz)) {
        d8classes.add(clazz);
        // TODO(b/309743298): Improve this to only visit each class once and stop at
        //  library boundary.
        appInfo.forEachSuperType(
            clazz,
            (superType, subclass, ignored) -> {
              DexProgramClass superClass = asProgramClassOrNull(appInfo.definitionFor(superType));
              if (superClass != null) {
                d8classes.add(superClass);
              }
            });
      }
    }

    // Filter the program input into the D8 and R8 parts.
    Set<String> d8ZipEntries =
        d8classes.stream()
            .map(clazz -> ZipUtils.zipEntryNameForClass(clazz.getClassReference()))
            .collect(Collectors.toSet());
    ZipBuilder d8ProgramBuilder = ZipBuilder.builder(resolveTmp("d8-program.jar"));
    ZipBuilder r8ProgramBuilder = ZipBuilder.builder(resolveTmp("r8-program.jar"));
    ZipUtils.iter(
        dump.getProgramArchive(),
        (entry, input) -> {
          if (d8ZipEntries.contains(entry.getName())) {
            d8ProgramBuilder.addBytes(entry.getName(), ByteStreams.toByteArray(input));
          } else {
            r8ProgramBuilder.addBytes(entry.getName(), ByteStreams.toByteArray(input));
          }
        });
    Path d8Program = d8ProgramBuilder.build();
    Path r8Program = r8ProgramBuilder.build();
    return new R8PartialInput(d8Program, r8Program, dump);
  }

  private R8PartialD8DexResult runD8DexStep(R8PartialInput input, ExecutorService executor)
      throws IOException {
    // Compile D8 input with D8.
    // TODO(b/389575762): Consume the DexProgramClasses directly instead of writing to a zip.
    Path d8Output = resolveTmp("d8-output.zip");
    D8Command.Builder d8Builder =
        D8Command.builder(options.reporter)
            .setOutput(d8Output, OutputMode.DexIndexed);
    input.configure(d8Builder);
    AndroidAppConsumers d8OutputAppSink = null;
    if (d8OutputAppConsumer != null) {
      d8OutputAppSink = new AndroidAppConsumers(d8Builder);
    }
    d8Builder.validate();
    D8Command d8Command = d8Builder.makeCommand();
    AndroidApp d8App = d8Command.getInputApp();
    if (d8InputAppConsumer != null) {
      d8InputAppConsumer.accept(d8App);
    }
    InternalOptions d8Options = d8Command.getInternalOptions();
    options.partialCompilationConfiguration.d8DexOptionsConsumer.accept(d8Options);
    d8Options.partialSubCompilationConfiguration =
        new R8PartialD8SubCompilationConfiguration(timing);
    D8.runInternal(d8App, d8Options, executor);
    if (d8OutputAppConsumer != null) {
      d8OutputAppConsumer.accept(d8OutputAppSink.build());
    }
    return new R8PartialD8DexResult(d8Output);
  }

  private R8PartialDesugarResult runDesugarStep(R8PartialInput input, ExecutorService executor)
      throws IOException {
    // TODO(b/389575762): Consume the DexProgramClasses instead of writing to a zip.
    // TODO(b/389039057): This will desugar the entire R8 part. For build speed, look into if some
    //  desugarings can be postponed to the R8 compilation, since we do not desugar dead code in R8.
    //  As a simple example, it should be safe to postpone backporting to the R8 compilation.
    // TODO(b/389039057): This runs a full D8 compilation. For build speed, consider if the various
    //  passes in D8 can be disabled when the `partialSubCompilationConfiguration` is set.
    Path desugarOutput = resolveTmp("desugar-output.zip");
    D8Command.Builder d8Builder =
        D8Command.builder(options.reporter).setOutput(desugarOutput, OutputMode.ClassFile);
    // TODO(b/390327883): This should enable intermediate mode.
    input.configureDesugar(d8Builder);
    d8Builder.validate();
    D8Command d8Command = d8Builder.makeCommand();
    AndroidApp d8App = d8Command.getInputApp();
    InternalOptions d8Options = d8Command.getInternalOptions();
    options.partialCompilationConfiguration.d8DesugarOptionsConsumer.accept(d8Options);
    d8Options.partialSubCompilationConfiguration =
        new R8PartialD8SubCompilationConfiguration(timing);
    D8.runInternal(d8App, d8Options, executor);
    return new R8PartialDesugarResult(desugarOutput);
  }

  // TODO(b/389031823): Parallelize trace references.
  private R8PartialTraceReferencesResult runTraceReferencesStep(
      R8PartialInput input, R8PartialDesugarResult desugarResult)
      throws IOException, ResourceException {
    // Run trace references to produce keep rules for the D8 compiled part.
    // TODO(b/309743298): Do not emit keep rules into a file.
    Path traceReferencesRules = resolveTmp("tr.rules");
    TraceReferencesKeepRules keepRulesConsumer =
        TraceReferencesKeepRules.builder()
            .setOutputConsumer(new FileConsumer(traceReferencesRules))
            .build();
    TraceReferencesCommand.Builder trBuilder =
        TraceReferencesCommand.builder()
            .addTargetFiles(desugarResult.getOutputPath())
            .setConsumer(keepRulesConsumer);
    input.configure(trBuilder);
    TraceReferencesCommand tr = TraceReferencesBridge.makeCommand(trBuilder);
    TraceReferencesBridge.runInternal(tr);
    return new R8PartialTraceReferencesResult(traceReferencesRules);
  }

  private R8PartialR8Result runR8PartialStep(
      R8PartialInput input,
      R8PartialDesugarResult desugarResult,
      R8PartialTraceReferencesResult traceReferencesResult,
      R8PartialTraceResourcesResult traceResourcesResult,
      ExecutorService executor)
      throws IOException {
    // Compile R8 input with R8 using the keep rules from trace references.
    Path r8Output = resolveTmp("r8-output.zip");
    R8PartialDataResourceConsumer r8DataResourcesConsumer = new R8PartialDataResourceConsumer();
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
            .addProgramFiles(desugarResult.getOutputPath())
            .addProguardConfigurationFiles(traceReferencesResult.getOutputPath())
            .enableLegacyFullModeForKeepRules(true)
            .setProgramConsumer(
                new ForwardingConsumer(new ArchiveConsumer(r8Output)) {
                  @Override
                  public DataResourceConsumer getDataResourceConsumer() {
                    return r8DataResourcesConsumer;
                  }
                });
    input.configure(r8Builder);
    AndroidAppConsumers r8OutputAppSink = null;
    if (r8OutputAppConsumer != null) {
      r8OutputAppSink = new AndroidAppConsumers(r8Builder);
    }
    r8Builder.validate();
    R8Command r8Command = r8Builder.makeCommand();
    AndroidApp r8App = r8Command.getInputApp();
    if (r8InputAppConsumer != null) {
      r8InputAppConsumer.accept(r8App);
    }
    InternalOptions r8Options = r8Command.getInternalOptions();
    options.partialCompilationConfiguration.r8OptionsConsumer.accept(r8Options);
    r8Options.partialSubCompilationConfiguration =
        new R8PartialR8SubCompilationConfiguration(
            traceResourcesResult.getResourceIdsToTrace(), timing);
    r8Options.mapConsumer = options.mapConsumer;
    if (options.androidResourceProvider != null) {
      r8Options.androidResourceProvider = options.androidResourceProvider;
      r8Options.androidResourceConsumer = options.androidResourceConsumer;
      r8Options.resourceShrinkerConfiguration = options.resourceShrinkerConfiguration;
    }
    R8.runInternal(r8App, r8Options, executor);
    if (r8OutputAppConsumer != null) {
      r8OutputAppConsumer.accept(r8OutputAppSink.build());
    }
    return new R8PartialR8Result(r8DataResourcesConsumer.getDataResources(), r8Output);
  }

  private void runD8MergeStep(
      R8PartialInput input,
      R8PartialD8DexResult d8DexResult,
      R8PartialR8Result r8Result,
      ExecutorService executor)
      throws IOException {
    // TODO(b/309743298): Handle jumbo string rewriting with PCs in mapping file.
    D8Command.Builder mergerBuilder =
        D8Command.builder(options.reporter)
            .addProgramFiles(d8DexResult.getOutputPath(), r8Result.getOutputPath())
            .setProgramConsumer(options.programConsumer);
    input.configureMerge(mergerBuilder);
    mergerBuilder.validate();
    D8Command mergeCommand = mergerBuilder.makeCommand();
    AndroidApp mergeApp = mergeCommand.getInputApp();
    InternalOptions mergeOptions = mergeCommand.getInternalOptions();
    options.partialCompilationConfiguration.d8MergeOptionsConsumer.accept(mergeOptions);
    mergeOptions.partialSubCompilationConfiguration =
        new R8PartialD8SubCompilationConfiguration(timing);
    D8.runInternal(mergeApp, mergeOptions, executor);
  }

  private Path resolveTmp(String string) throws IOException {
    return options.partialCompilationConfiguration.getTempDir().resolve(string);
  }
}
