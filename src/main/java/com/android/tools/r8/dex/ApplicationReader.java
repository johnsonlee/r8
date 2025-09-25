// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex;

import static com.android.tools.r8.graph.ClassKind.CLASSPATH;
import static com.android.tools.r8.graph.ClassKind.LIBRARY;
import static com.android.tools.r8.graph.ClassKind.PROGRAM;
import static com.android.tools.r8.utils.ExceptionUtils.unwrapExecutionException;

import com.android.tools.r8.ClassFileResourceProvider;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.R8Command.EnsureNonDexProgramResourceProvider;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.StringResource;
import com.android.tools.r8.dump.DumpOptions;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.UnsupportedMainDexListUsageDiagnostic;
import com.android.tools.r8.graph.ApplicationReaderMap;
import com.android.tools.r8.graph.ClassKind;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexApplicationReadFlags;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClasspathClass;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexLibraryClass;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.graph.JarApplicationReader;
import com.android.tools.r8.graph.JarClassFileReader;
import com.android.tools.r8.graph.LazyLoadedDexApplication;
import com.android.tools.r8.graph.LazyLoadedDexApplication.AllClasses;
import com.android.tools.r8.keepanno.ast.KeepDeclaration;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.MainDexInfo;
import com.android.tools.r8.threading.TaskCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.ClassProvider;
import com.android.tools.r8.utils.ClasspathClassCollection;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.DexVersion;
import com.android.tools.r8.utils.DumpInputFlags;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.LibraryClassCollection;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.MainDexListParser;
import com.android.tools.r8.utils.ProgramClassCollection;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ApplicationReader {

  private final InternalOptions options;
  private final DexItemFactory itemFactory;
  private final Timing timing;
  private final AndroidApp inputApp;

  private DexApplicationReadFlags flags;

  public interface ProgramClassConflictResolver {
    DexProgramClass resolveClassConflict(DexProgramClass a, DexProgramClass b);
  }

  public ApplicationReader(AndroidApp inputApp, InternalOptions options, Timing timing) {
    this.options = options;
    itemFactory = options.itemFactory;
    this.timing = timing;
    this.inputApp = inputApp;
  }

  public LazyLoadedDexApplication read() throws IOException {
    return read((StringResource) null);
  }

  public LazyLoadedDexApplication read(StringResource proguardMap) throws IOException {
    ExecutorService executor = options.getThreadingModule().createSingleThreadedExecutorService();
    try {
      return read(proguardMap, executor);
    } finally {
      executor.shutdown();
    }
  }

  public final LazyLoadedDexApplication read(ExecutorService executorService) throws IOException {
    return read(inputApp.getProguardMapInputData(), executorService);
  }

  public final LazyLoadedDexApplication readWithoutDumping(ExecutorService executorService)
      throws IOException {
    return read(inputApp.getProguardMapInputData(), executorService, DumpInputFlags.noDump());
  }

  public final LazyLoadedDexApplication read(
      StringResource proguardMap,
      ExecutorService executorService)
      throws IOException {
    return read(proguardMap, executorService, options.getDumpInputFlags());
  }

  public final LazyLoadedDexApplication read(
      StringResource proguardMap,
      ExecutorService executorService,
      DumpInputFlags dumpInputFlags)
      throws IOException {
    assert verifyMainDexOptionsCompatible(inputApp, options);
    dumpApplication(dumpInputFlags);

    if (options.testing.verifyInputs) {
      inputApp.validateInputs();
    }

    timing.begin("DexApplication.read");
    LazyLoadedDexApplication.Builder builder = DexApplication.builder(options, timing);
    TaskCollection<?> tasks = new TaskCollection<>(options, executorService);
    try {
      // Still preload some of the classes, primarily for two reasons:
      // (a) class lazy loading is not supported for DEX files
      //     now and current implementation of parallel DEX file
      //     loading will be lost with on-demand class loading.
      // (b) some of the class file resources don't provide information
      //     about class descriptor.
      // TODO: try and preload less classes.
      readProguardMap(proguardMap, builder, tasks);
      ClassReader classReader = new ClassReader(tasks);
      classReader.initializeLazyClassCollection(builder);
      classReader.readSources();
      timing.time("Await read", () -> tasks.await());
      flags = classReader.getDexApplicationReadFlags();
      return builder
          .addDataResourceProviders(inputApp.getProgramResourceProviders())
          .addProgramClasses(classReader.programClasses)
          .setFlags(flags)
          .setKeepDeclarations(classReader.getKeepDeclarations())
          .build();
    } catch (ExecutionException e) {
      throw unwrapExecutionException(e);
    } catch (ResourceException e) {
      throw options.reporter.fatalError(new StringDiagnostic(e.getMessage(), e.getOrigin()));
    } finally {
      timing.end();
    }
  }

  @Deprecated
  public DirectMappedDexApplication readDirectSingleThreaded() throws IOException {
    ExecutorService executor = options.getThreadingModule().createSingleThreadedExecutorService();
    try {
      return readDirect(executor);
    } finally {
      executor.shutdown();
    }
  }

  public DirectMappedDexApplication readDirect(ExecutorService executorService) throws IOException {
    assert verifyMainDexOptionsCompatible(inputApp, options);
    dumpApplication(options.getDumpInputFlags());

    if (options.testing.verifyInputs) {
      inputApp.validateInputs();
    }

    timing.begin("DexApplication.readDirect");
    DirectMappedDexApplication.Builder builder =
        DirectMappedDexApplication.directBuilder(options, timing);
    TaskCollection<?> tasks = new TaskCollection<>(options, executorService);
    try {
      readProguardMap(inputApp.getProguardMapInputData(), builder, tasks);
      ClassReader classReader = new ClassReader(tasks);
      AllClasses.Builder allClassesBuilder = AllClasses.builder();
      classReader.acceptClasspathAndLibraryClassCollections(
          allClassesBuilder::setClasspathClasses, allClassesBuilder::setLibraryClasses);
      allClassesBuilder.forceLoadNonProgramClassCollections(options, tasks, timing);
      classReader.readSources();
      timing.time("Await read", () -> tasks.await());
      allClassesBuilder.setProgramClasses(
          ProgramClassCollection.resolveConflicts(classReader.programClasses, options));
      AllClasses allClasses = allClassesBuilder.build(options, timing);
      flags = classReader.getDexApplicationReadFlags();
      return builder
          .addDataResourceProviders(inputApp.getProgramResourceProviders())
          .addProgramClasses(allClasses.getProgramClasses())
          .replaceClasspathClasses(allClasses.getClasspathClasses())
          .replaceLibraryClasses(allClasses.getLibraryClasses())
          .setFlags(flags)
          .setKeepDeclarations(classReader.getKeepDeclarations())
          .build();
    } catch (ExecutionException e) {
      throw unwrapExecutionException(e);
    } catch (ResourceException e) {
      throw options.reporter.fatalError(new StringDiagnostic(e.getMessage(), e.getOrigin()));
    } finally {
      timing.end();
    }
  }

  public final void dump(DumpInputFlags dumpInputFlags) {
    assert verifyMainDexOptionsCompatible(inputApp, options);
    dumpApplication(dumpInputFlags);
  }

  private void dumpApplication(DumpInputFlags dumpInputFlags) {
    DumpOptions dumpOptions = options.dumpOptions;
    if (dumpOptions == null
        || options.partialSubCompilationConfiguration != null
        || !dumpInputFlags.shouldDump(dumpOptions)) {
      return;
    }
    Path dumpOutput = dumpInputFlags.getDumpPath();
    timing.begin("ApplicationReader.dump");
    inputApp.dump(dumpOutput, dumpOptions, options);
    timing.end();
    Diagnostic message = new StringDiagnostic("Dumped compilation inputs to: " + dumpOutput);
    if (dumpInputFlags.shouldFailCompilation()) {
      throw options.reporter.fatalError(message);
    } else if (dumpInputFlags.shouldLogDumpInfoMessage()) {
      options.reporter.info(message);
    }
  }

  public MainDexInfo readMainDexClasses(DexApplication app) {
    return readMainDexClasses(app, flags.hasReadProgramClassFromCf());
  }

  public MainDexInfo readMainDexClassesForR8(DexApplication app) {
    // Officially R8 only support reading CF program inputs, thus we always generate a deprecated
    // diagnostic if main-dex list is used.
    return readMainDexClasses(app, true);
  }

  private MainDexInfo readMainDexClasses(DexApplication app, boolean emitDeprecatedDiagnostics) {
    MainDexInfo.Builder builder = MainDexInfo.none().builder();
    if (inputApp.hasMainDexList()) {
      for (StringResource resource : inputApp.getMainDexListResources()) {
        if (emitDeprecatedDiagnostics) {
          options.reporter.error(new UnsupportedMainDexListUsageDiagnostic(resource.getOrigin()));
        }
        addToMainDexClasses(app, builder, MainDexListParser.parseList(resource, itemFactory));
      }
      if (!inputApp.getMainDexClasses().isEmpty()) {
        if (emitDeprecatedDiagnostics) {
          options.reporter.error(new UnsupportedMainDexListUsageDiagnostic(Origin.unknown()));
        }
        addToMainDexClasses(
            app,
            builder,
            inputApp.getMainDexClasses().stream()
                .map(clazz -> itemFactory.createType(DescriptorUtils.javaTypeToDescriptor(clazz)))
                .collect(Collectors.toList()));
      }
    }
    return builder.buildList();
  }

  private void addToMainDexClasses(
      DexApplication app, MainDexInfo.Builder builder, Iterable<DexType> types) {
    for (DexType type : types) {
      DexProgramClass clazz = app.programDefinitionFor(type);
      if (clazz != null) {
        builder.addList(clazz);
      } else if (!options.ignoreMainDexMissingClasses) {
        options.reporter.warning(
            new StringDiagnostic(
                "Application does not contain `"
                    + type.toSourceString()
                    + "` as referenced in main-dex-list."));
      }
    }
  }

  private static boolean verifyMainDexOptionsCompatible(
      AndroidApp inputApp, InternalOptions options) {
    if (!options.isGeneratingDex()) {
      return true;
    }
    // Native multidex is supported from L, but the compiler supports compiling to L/21 using
    // legacy multidex as there are some devices that have issues with it still.
    AndroidApiLevel nativeMultiDex = AndroidApiLevel.L_MR1;
    if (options.getMinApiLevel().isLessThan(nativeMultiDex)) {
      return true;
    }
    assert options.mainDexKeepRules.isEmpty();
    assert options.mainDexListConsumer == null;
    assert !inputApp.hasMainDexList();
    return true;
  }

  private AndroidApiLevel validateOrComputeMinApiLevel(
      AndroidApiLevel computedMinApiLevel, DexReader dexReader) {
    DexVersion version = dexReader.getDexVersion();
    if (options.getMinApiLevel() == AndroidApiLevel.getDefault()) {
      computedMinApiLevel = computedMinApiLevel.max(AndroidApiLevel.getMinAndroidApiLevel(version));
    } else if (!version.matchesApiLevel(options.getMinApiLevel())) {
      throwIncompatibleDexVersionAndMinApi(version);
    }
    return computedMinApiLevel;
  }

  private void throwIncompatibleDexVersionAndMinApi(DexVersion version) {
    throw new CompilationError(
        "Dex file with version '"
            + version.getIntValue()
            + "' cannot be used with min sdk level '"
            + options.getMinApiLevel()
            + "'.");
  }

  private void readProguardMap(
      StringResource map, DexApplication.Builder<?, ?> builder, TaskCollection<?> tasks)
      throws ExecutionException {
    // Read the Proguard mapping file in parallel with DexCode and DexProgramClass items.
    if (map == null) {
      return;
    }
    tasks.submit(
        () -> {
          try {
            builder.setProguardMap(
                ClassNameMapper.mapperFromString(
                    map.getString(),
                    options.reporter,
                    options.mappingComposeOptions().allowEmptyMappedRanges,
                    options.testing.enableExperimentalMapFileVersion,
                    true));
          } catch (IOException | ResourceException e) {
            throw new CompilationError("Failure to read proguard map file", e, map.getOrigin());
          }
        });
  }

  private final class ClassReader {
    private final TaskCollection<?> tasks;

    // We use concurrent queues to collect classes since the classes can be collected concurrently.
    private final Queue<DexProgramClass> programClasses = new ConcurrentLinkedQueue<>();
    // Jar application reader to share across all class readers.
    private final DexApplicationReadFlags.Builder readFlagsBuilder =
        DexApplicationReadFlags.builder();
    private final JarApplicationReader application =
        new JarApplicationReader(options, readFlagsBuilder);

    // Flag of which input resource types have flown into the program classes.
    // Note that this is just at the level of the resources having been given.
    // It is possible to have, e.g., an empty dex file, so no classes, but this will still be true
    // as there was a dex resource.
    private boolean hasReadProgramResourceFromCf = false;
    private boolean hasReadProgramResourceFromDex = false;

    ClassReader(TaskCollection<?> tasks) {
      this.tasks = tasks;
    }

    public DexApplicationReadFlags getDexApplicationReadFlags() {
      if (options.partialSubCompilationConfiguration != null) {
        return options.partialSubCompilationConfiguration.getFlags();
      }
      return readFlagsBuilder
          .setHasReadProgramClassFromDex(hasReadProgramResourceFromDex)
          .setHasReadProgramClassFromCf(hasReadProgramResourceFromCf)
          .build();
    }

    public List<KeepDeclaration> getKeepDeclarations() {
      return application.getKeepDeclarations();
    }

    private void readDexSources(List<ProgramResource> dexSources)
        throws IOException, ResourceException, ExecutionException {
      if (dexSources.isEmpty()) {
        return;
      }
      List<DexParser<DexProgramClass>> dexParsers = new ArrayList<>(dexSources.size());
      List<DexParser<DexProgramClass>> dexContainerParsers = new ArrayList<>(4);
      AndroidApiLevel computedMinApiLevel = options.getMinApiLevel();
      for (ProgramResource input : dexSources) {
        DexReader dexReader = new DexReader(input);
        if (options.passthroughDexCode) {
          if (!options.getTestingOptions().forceDexContainerFormat) {
            computedMinApiLevel = validateOrComputeMinApiLevel(computedMinApiLevel, dexReader);
          } else {
            // Allow forcing DEX container format independent of min API level.
            assert dexReader.getDexVersion().isGreaterThanOrEqualTo(DexVersion.V41);
          }
        }
        if (!dexReader.getDexVersion().isContainerDex()) {
          dexParsers.add(new DexParser<>(dexReader, PROGRAM, options));
        } else {
          addDexParsersForContainer(dexContainerParsers, dexReader);
        }
      }

      options.setMinApiLevel(computedMinApiLevel);
      for (DexParser<DexProgramClass> dexParser :
          Iterables.concat(dexParsers, dexContainerParsers)) {
        dexParser.populateIndexTables();
      }
      if (!options.skipReadingDexCode) {
        ApplicationReaderMap applicationReaderMap = ApplicationReaderMap.getInstance(options);
        // Read the DexCode items and DexProgramClass items in parallel.
        for (DexParser<DexProgramClass> dexParser : dexParsers) {
          // Depends on Methods, Code items etc.
          tasks.submit(() -> dexParser.addClassDefsTo(programClasses, applicationReaderMap));
        }
        // All DEX parsers for container sections use the same DEX reader,
        // so don't process in parallel.
        for (DexParser<DexProgramClass> dexParser : dexContainerParsers) {
          dexParser.addClassDefsTo(programClasses, applicationReaderMap);
        }
      }
    }

    private void addDexParsersForContainer(
        List<DexParser<DexProgramClass>> dexParsers, DexReader dexReader) {
      // Find the start offsets of each dex section.
      dexParsers.addAll(DexParser.getDexParsersForContainerFormat(dexReader, options));
    }

    private boolean includeAnnotationClass(DexProgramClass clazz) {
      if (!options.pruneNonVissibleAnnotationClasses) {
        return true;
      }
      DexAnnotation retentionAnnotation =
          clazz.annotations().getFirstMatching(itemFactory.retentionType);
      // Default is CLASS retention, read if retained.
      if (retentionAnnotation == null) {
        return DexAnnotation.retainCompileTimeAnnotation(clazz.getType(), application.options);
      }
      // Otherwise only read runtime visible annotations.
      return retentionAnnotation.annotation.toString().contains("RUNTIME");
    }

    void readSources() throws IOException, ResourceException, ExecutionException {
      timing.begin("Compute all program resources");
      List<ProgramResource> dexResources = new ArrayList<>();
      JarClassFileReader<DexProgramClass> cfReader =
          new JarClassFileReader<>(
              application,
              clazz -> {
                if (clazz.isAnnotation() && !includeAnnotationClass(clazz)) {
                  return;
                }
                programClasses.add(clazz);
              },
              PROGRAM);
      inputApp.computeAllProgramResources(
          resource -> {
            if (resource.getKind() == ProgramResource.Kind.CF) {
              hasReadProgramResourceFromCf = true;
              tasks.submitUnchecked(
                  () -> {
                    Timing threadTiming =
                        timing.createThreadTiming("Read program resource", options);
                    cfReader.read(resource);
                    threadTiming.end().notifyThreadTimingFinished();
                  });
            } else {
              assert resource.getKind() == ProgramResource.Kind.DEX;
              dexResources.add(resource);
            }
          },
          internalProvider -> programClasses.addAll(internalProvider.getClasses()),
          legacyProgramResourceProvider ->
              options.reporter.warning(
                  "Program resource provider does not support async parsing: "
                      + EnsureNonDexProgramResourceProvider.unwrap(legacyProgramResourceProvider)
                          .getClass()
                          .getTypeName()),
          timing);
      hasReadProgramResourceFromDex = !dexResources.isEmpty();
      timing.end();
      readDexSources(dexResources);
    }

    private <T extends DexClass> ClassProvider<T> buildClassProvider(
        ClassKind<T> classKind, List<ClassFileResourceProvider> resourceProviders) {
      return ClassProvider.combine(
          classKind,
          ListUtils.map(
              resourceProviders,
              resourceProvider ->
                  ClassProvider.forClassFileResources(classKind, resourceProvider, application)));
    }

    void acceptClasspathAndLibraryClassCollections(
        Consumer<ClasspathClassCollection> classpathClassCollectionConsumer,
        Consumer<LibraryClassCollection> libraryClassCollectionConsumer) {
      // Create classpath class collection if needed.
      ClassProvider<DexClasspathClass> classpathClassProvider =
          buildClassProvider(CLASSPATH, inputApp.getClasspathResourceProviders());
      if (classpathClassProvider != null) {
        classpathClassCollectionConsumer.accept(
            new ClasspathClassCollection(classpathClassProvider));
      }

      // Create library class collection if needed.
      ClassProvider<DexLibraryClass> libraryClassProvider =
          buildClassProvider(LIBRARY, inputApp.getLibraryResourceProviders());
      if (libraryClassProvider != null) {
        libraryClassCollectionConsumer.accept(new LibraryClassCollection(libraryClassProvider));
      }
    }

    void initializeLazyClassCollection(LazyLoadedDexApplication.Builder builder) {
      acceptClasspathAndLibraryClassCollections(
          builder::setClasspathClassCollection, builder::setLibraryClassCollection);
    }
  }
}
