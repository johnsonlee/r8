// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.tools.r8.DexIndexedConsumer.ArchiveConsumer;
import com.android.tools.r8.DexIndexedConsumer.ForwardingConsumer;
import com.android.tools.r8.StringConsumer.FileConsumer;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.dump.CompilerDump;
import com.android.tools.r8.dump.DumpOptions;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.naming.MapConsumer;
import com.android.tools.r8.synthesis.SyntheticItems.GlobalSyntheticsStrategy;
import com.android.tools.r8.tracereferences.TraceReferencesBridge;
import com.android.tools.r8.tracereferences.TraceReferencesCommand;
import com.android.tools.r8.tracereferences.TraceReferencesKeepRules;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.AndroidAppConsumers;
import com.android.tools.r8.utils.DumpInputFlags;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.stream.Collectors;

class R8Partial {

  private final InternalOptions options;
  private final Consumer<AndroidApp> r8InputAppConsumer;
  private final Consumer<AndroidApp> d8InputAppConsumer;
  private final Consumer<AndroidApp> r8OutputAppConsumer;
  private final Consumer<AndroidApp> d8OutputAppConsumer;

  R8Partial(InternalOptions options) {
    this.options = options;
    this.r8InputAppConsumer = options.partialCompilationConfiguration.r8InputAppConsumer;
    this.d8InputAppConsumer = options.partialCompilationConfiguration.d8InputAppConsumer;
    this.r8OutputAppConsumer = options.partialCompilationConfiguration.r8OutputAppConsumer;
    this.d8OutputAppConsumer = options.partialCompilationConfiguration.d8OutputAppConsumer;
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
    Timing timing = Timing.create("R8 partial " + Version.LABEL, options);

    ProgramConsumer originalProgramConsumer = options.programConsumer;
    DataResourceConsumer originalDataResourceConsumer = options.dataResourceConsumer;
    MapConsumer originalMapConsumer = options.mapConsumer;
    if (!(originalProgramConsumer instanceof DexIndexedConsumer)) {
      throw options.reporter.fatalError(
          "Partial shrinking does not support generating class files");
    }

    Path tmp = options.partialCompilationConfiguration.getTempDir();
    Path dumpFile = options.partialCompilationConfiguration.getDumpFile();

    // Create a dump of the compiler input.
    // TODO(b/309743298): Do not use compiler dump to handle splitting the compilation. This should
    // be all in memory.
    ApplicationReader applicationReader = new ApplicationReader(app, options, timing);
    applicationReader.dump(
        new DumpInputFlags() {

          @Override
          public Path getDumpPath() {
            return dumpFile;
          }

          @Override
          public boolean shouldDump(DumpOptions options) {
            return true;
          }

          @Override
          public boolean shouldFailCompilation() {
            return false;
          }

          @Override
          public boolean shouldLogDumpInfoMessage() {
            return false;
          }
        });
    CompilerDump dump = CompilerDump.fromArchive(dumpFile, tmp);
    if (dump.getBuildProperties().hasMainDexKeepRules()
        || dump.getBuildProperties().hasArtProfileProviders()
        || dump.getBuildProperties().hasStartupProfileProviders()) {
      throw options.reporter.fatalError(
          "Partial shrinking does not support legacy multi-dex, baseline or startup profiles");
    }

    DexApplication dapp = applicationReader.read().toDirect();
    AppInfoWithClassHierarchy appInfo =
        AppInfoWithClassHierarchy.createForDesugaring(
            AppInfo.createInitialAppInfo(dapp, GlobalSyntheticsStrategy.forNonSynthesizing()));

    Set<DexProgramClass> d8classes = new HashSet<>();
    appInfo
        .classes()
        .forEach(
            clazz -> {
              if (!d8classes.contains(clazz)
                  && !options.partialCompilationConfiguration.test(
                      clazz.getType().getDescriptor())) {
                d8classes.add(clazz);
                // TODO(b/309743298): Improve this to only visit each class once and stop at
                //  library boundary.
                appInfo.forEachSuperType(
                    clazz,
                    (superType, subclass, ignored) -> {
                      DexProgramClass superClass =
                          asProgramClassOrNull(appInfo.definitionFor(superType));
                      if (superClass != null) {
                        d8classes.add(superClass);
                      }
                    });
              }
            });

    // Filter the program input into the D8 and R8 parts.
    Set<String> d8ZipEntries =
        d8classes.stream()
            .map(clazz -> ZipUtils.zipEntryNameForClass(clazz.getClassReference()))
            .collect(Collectors.toSet());
    ZipBuilder d8ProgramBuilder = ZipBuilder.builder(tmp.resolve("d8-program.jar"));
    ZipBuilder r8ProgramBuilder = ZipBuilder.builder(tmp.resolve("r8-program.jar"));
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

    // Compile D8 input with D8.
    Path d8Output = tmp.resolve("d8-output.zip");
    D8Command.Builder d8Builder =
        D8Command.builder()
            .setMinApiLevel(dump.getBuildProperties().getMinApi())
            .addLibraryFiles(dump.getLibraryArchive())
            .addClasspathFiles(dump.getClasspathArchive())
            .addClasspathFiles(r8Program)
            .addProgramFiles(d8Program)
            .setMode(dump.getBuildProperties().getCompilationMode())
            .setOutput(d8Output, OutputMode.DexIndexed);
    if (dump.hasDesugaredLibrary()) {
      d8Builder.addDesugaredLibraryConfiguration(
          Files.readString(dump.getDesugaredLibraryFile(), UTF_8));
    }
    AndroidAppConsumers d8OutputAppSink = null;
    if (d8OutputAppConsumer != null) {
      d8OutputAppSink = new AndroidAppConsumers(d8Builder);
    }
    d8Builder.validate();
    D8Command d8command = d8Builder.makeCommand();
    AndroidApp d8App = d8command.getInputApp();
    if (d8InputAppConsumer != null) {
      d8InputAppConsumer.accept(d8App);
    }
    InternalOptions d8Options = d8command.getInternalOptions();
    assert d8Options.getMinApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.N)
        : "Default interface methods not yet supported";
    D8.runInternal(d8App, d8Options, executor);
    if (d8OutputAppConsumer != null) {
      d8OutputAppConsumer.accept(d8OutputAppSink.build());
    }

    // Run trace references to produce keep rules for the D8 compiled part.
    // TODO(b/309743298): Do not emit keep rules into a file.
    Path traceReferencesRules = tmp.resolve("tr.rules");
    TraceReferencesKeepRules keepRulesConsumer =
        TraceReferencesKeepRules.builder()
            .setOutputConsumer(new FileConsumer(traceReferencesRules))
            .build();
    TraceReferencesCommand.Builder trBuilder =
        TraceReferencesCommand.builder()
            .setConsumer(keepRulesConsumer)
            .addLibraryFiles(dump.getLibraryArchive())
            .addTargetFiles(r8Program)
            .addSourceFiles(d8Program);
    TraceReferencesCommand tr = TraceReferencesBridge.makeCommand(trBuilder);
    TraceReferencesBridge.runInternal(tr);

    class R8DataResources implements DataResourceConsumer {
      final List<DataResource> dataResources = new ArrayList<>();

      @Override
      public void accept(DataDirectoryResource directory, DiagnosticsHandler diagnosticsHandler) {
        dataResources.add(directory);
      }

      @Override
      public void accept(DataEntryResource file, DiagnosticsHandler diagnosticsHandler) {
        dataResources.add(file);
      }

      @Override
      public void finished(DiagnosticsHandler handler) {}

      public void feed(DataResourceConsumer consumer, DiagnosticsHandler handler) {
        dataResources.forEach(
            dataResource -> {
              if (dataResource instanceof DataDirectoryResource) {
                consumer.accept((DataDirectoryResource) dataResource, handler);
              } else {
                assert dataResource instanceof DataEntryResource;
                consumer.accept((DataEntryResource) dataResource, handler);
              }
            });
      }
    }

    // Compile R8 input with R8 using the keep rules from trace references.
    Path r8Output = tmp.resolve("r8-output.zip");
    R8DataResources r8DataResources = new R8DataResources();
    R8Command.Builder r8Builder =
        R8Command.builder()
            .setMinApiLevel(dump.getBuildProperties().getMinApi())
            .addLibraryFiles(dump.getLibraryArchive())
            .addClasspathFiles(dump.getClasspathArchive())
            .addClasspathFiles(d8Program)
            .addProgramFiles(r8Program)
            .addProguardConfigurationFiles(dump.getProguardConfigFile(), traceReferencesRules)
            .enableLegacyFullModeForKeepRules(true)
            .setMode(dump.getBuildProperties().getCompilationMode())
            .setProgramConsumer(
                new ForwardingConsumer(new ArchiveConsumer(r8Output)) {
                  @Override
                  public DataResourceConsumer getDataResourceConsumer() {
                    return r8DataResources;
                  }
                });
    if (dump.hasDesugaredLibrary()) {
      r8Builder.addDesugaredLibraryConfiguration(
          Files.readString(dump.getDesugaredLibraryFile(), UTF_8));
    }
    AndroidAppConsumers r8OutputAppSink = null;
    if (r8Builder != null) {
      r8OutputAppSink = new AndroidAppConsumers(r8Builder);
    }
    r8Builder.validate();
    R8Command r8Command = r8Builder.makeCommand();
    AndroidApp r8App = r8Command.getInputApp();
    if (r8InputAppConsumer != null) {
      r8InputAppConsumer.accept(r8App);
    }
    InternalOptions r8Options = r8Command.getInternalOptions();
    r8Options.mapConsumer = originalMapConsumer;
    r8Options.quiet = true; // Don't write the R8 version.
    R8.runInternal(r8App, r8Options, executor);
    if (r8OutputAppConsumer != null) {
      r8OutputAppConsumer.accept(r8OutputAppSink.build());
    }

    // TODO(b/309743298): Handle jumbo string rewriting with PCs in mapping file.
    D8Command.Builder mergerBuilder =
        D8Command.builder()
            .setMinApiLevel(dump.getBuildProperties().getMinApi())
            .addLibraryFiles(dump.getLibraryArchive())
            .addClasspathFiles(dump.getClasspathArchive())
            .addProgramFiles(d8Output, r8Output)
            .setMode(dump.getBuildProperties().getCompilationMode())
            .setProgramConsumer(originalProgramConsumer);
    mergerBuilder.validate();
    D8Command mergeCommand = mergerBuilder.makeCommand();
    AndroidApp mergeApp = mergeCommand.getInputApp();
    InternalOptions mergeOptions = mergeCommand.getInternalOptions();
    D8.runInternal(mergeApp, mergeOptions, executor);
    // Feed the data resource output by R8 to the output consumer. Keeping this at the end after the
    // merge keeps the order of calls to the output consumer closer to full R8.
    if (originalDataResourceConsumer != null) {
      r8DataResources.feed(originalDataResourceConsumer, options.reporter);
      originalDataResourceConsumer.finished(options.reporter);
    }
    timing.end();
  }
}
