// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.diagnostic.R8VersionDiagnostic;
import com.android.tools.r8.dump.CompilerDump;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.partial.R8PartialD8DexResult;
import com.android.tools.r8.partial.R8PartialDesugarResult;
import com.android.tools.r8.partial.R8PartialInput;
import com.android.tools.r8.partial.R8PartialInputToDumpFlags;
import com.android.tools.r8.partial.R8PartialSubCompilationConfiguration.R8PartialD8DesugarSubCompilationConfiguration;
import com.android.tools.r8.partial.R8PartialSubCompilationConfiguration.R8PartialD8DexSubCompilationConfiguration;
import com.android.tools.r8.partial.R8PartialSubCompilationConfiguration.R8PartialR8SubCompilationConfiguration;
import com.android.tools.r8.synthesis.SyntheticItems.GlobalSyntheticsStrategy;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.ForwardingDiagnosticsHandler;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
    if (!(options.programConsumer instanceof DexIndexedConsumer)) {
      throw options.reporter.fatalError(
          "Partial shrinking does not support generating class files");
    }

    timing.begin("Process input");
    R8PartialInput input = runProcessInputStep(app);

    timing.end().begin("Run dexing");
    R8PartialD8DexResult dexingResult = runDexingStep(input, executor);

    timing.end().begin("Run desugaring");
    R8PartialDesugarResult desugarResult = runDesugarStep(input, executor);

    timing.end().begin("Run R8");
    runR8PartialStep(input, dexingResult, desugarResult, executor);
    timing.end();

    if (options.isPrintTimesReportingEnabled()) {
      timing.report();
    }
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

  private R8PartialD8DexResult runDexingStep(R8PartialInput input, ExecutorService executor)
      throws IOException {
    // Compile D8 input with D8.
    D8Command.Builder d8Builder =
        D8Command.builder(options.reporter).setProgramConsumer(DexIndexedConsumer.emptyConsumer());
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
        D8Command.builder(options.reporter).setProgramConsumer(ClassFileConsumer.emptyConsumer());
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
            .enableLegacyFullModeForKeepRules(true)
            .setProgramConsumer(options.programConsumer);
    input.configure(r8Builder);
    r8Builder.validate();
    R8Command r8Command = r8Builder.makeR8Command(options.dexItemFactory());
    AndroidApp r8App = r8Command.getInputApp();
    if (r8InputAppConsumer != null) {
      r8InputAppConsumer.accept(r8App);
    }
    InternalOptions r8Options = r8Command.getInternalOptions();
    options.partialCompilationConfiguration.r8OptionsConsumer.accept(r8Options);
    r8Options.partialSubCompilationConfiguration =
        new R8PartialR8SubCompilationConfiguration(
            desugarResult.getOutputClasses(), dexingResult.getOutputClasses(), timing);
    r8Options.mapConsumer = options.mapConsumer;
    if (options.androidResourceProvider != null) {
      r8Options.androidResourceProvider = options.androidResourceProvider;
      r8Options.androidResourceConsumer = options.androidResourceConsumer;
      r8Options.resourceShrinkerConfiguration = options.resourceShrinkerConfiguration;
    }
    R8.runInternal(r8App, r8Options, executor);
  }

  private Path resolveTmp(String string) throws IOException {
    return options.partialCompilationConfiguration.getTempDir().resolve(string);
  }
}
