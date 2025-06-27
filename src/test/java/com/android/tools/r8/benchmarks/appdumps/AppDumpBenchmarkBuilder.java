// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks.appdumps;


import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.LibraryDesugaringTestConfiguration;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8PartialTestBuilder;
import com.android.tools.r8.R8PartialTestCompileResult;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.benchmarks.BenchmarkBase;
import com.android.tools.r8.benchmarks.BenchmarkConfig;
import com.android.tools.r8.benchmarks.BenchmarkConfigError;
import com.android.tools.r8.benchmarks.BenchmarkDependency;
import com.android.tools.r8.benchmarks.BenchmarkEnvironment;
import com.android.tools.r8.benchmarks.BenchmarkMethod;
import com.android.tools.r8.benchmarks.BenchmarkMetric;
import com.android.tools.r8.benchmarks.BenchmarkResultsSingle;
import com.android.tools.r8.benchmarks.BenchmarkSuite;
import com.android.tools.r8.benchmarks.BenchmarkTarget;
import com.android.tools.r8.dump.CompilerDump;
import com.android.tools.r8.dump.DumpOptions;
import com.android.tools.r8.keepanno.annotations.AnnotationPattern;
import com.android.tools.r8.keepanno.annotations.KeepEdge;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.KeepTarget;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.lang.annotation.RetentionPolicy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class AppDumpBenchmarkBuilder {

  @KeepEdge(
      consequences =
          @KeepTarget(
              kind = KeepItemKind.ONLY_METHODS,
              methodAnnotatedByClassName = "androidx.compose.runtime.Composable",
              constraints = {},
              constrainAnnotations =
                  @AnnotationPattern(
                      name = "androidx.compose.runtime.Composable",
                      retention = RetentionPolicy.CLASS)))
  static class KeepComposableAnnotations {}

  public static AppDumpBenchmarkBuilder builder() {
    return new AppDumpBenchmarkBuilder();
  }

  private String name;
  private BenchmarkDependency dumpDependency;
  private int fromRevision = -1;
  private CompilationMode compilationMode;
  private boolean enableLibraryDesugaring = true;
  private boolean enableResourceShrinking = false;
  private boolean runtimeOnly = false;
  private int warmupIterations = 1;
  private final List<String> programPackages = new ArrayList<>();

  public void verify() {
    if (name == null) {
      throw new BenchmarkConfigError("Missing name");
    }
    if (dumpDependency == null) {
      throw new BenchmarkConfigError("Missing dump");
    }
  }

  public AppDumpBenchmarkBuilder setCompilationMode(CompilationMode compilationMode) {
    this.compilationMode = compilationMode;
    return this;
  }

  public AppDumpBenchmarkBuilder setEnableLibraryDesugaring(boolean enableLibraryDesugaring) {
    this.enableLibraryDesugaring = enableLibraryDesugaring;
    return this;
  }

  public AppDumpBenchmarkBuilder setEnableResourceShrinking(boolean enableResourceShrinking) {
    this.enableResourceShrinking = enableResourceShrinking;
    return this;
  }

  public AppDumpBenchmarkBuilder setName(String name) {
    this.name = name;
    return this;
  }

  public AppDumpBenchmarkBuilder setDumpDependencyPath(Path dumpDependencyPath) {
    return setDumpDependency(
        new BenchmarkDependency(
            "appdump",
            dumpDependencyPath.getFileName().toString(),
            dumpDependencyPath.getParent()));
  }

  public AppDumpBenchmarkBuilder setDumpDependency(BenchmarkDependency dependency) {
    this.dumpDependency = dependency;
    return this;
  }

  public AppDumpBenchmarkBuilder setFromRevision(int fromRevision) {
    this.fromRevision = fromRevision;
    return this;
  }

  public AppDumpBenchmarkBuilder setRuntimeOnly() {
    this.runtimeOnly = true;
    return this;
  }

  public AppDumpBenchmarkBuilder setWarmupIterations(int warmupIterations) {
    this.warmupIterations = warmupIterations;
    return this;
  }

  public AppDumpBenchmarkBuilder addProgramPackages(String... pkgs) {
    return addProgramPackages(Arrays.asList(pkgs));
  }

  public AppDumpBenchmarkBuilder addProgramPackages(Collection<String> pkgs) {
    programPackages.addAll(pkgs);
    return this;
  }

  public BenchmarkConfig buildR8() {
    return buildR8(getDefaultR8Configuration());
  }

  public BenchmarkConfig buildR8(ThrowableConsumer<? super R8FullTestBuilder> configuration) {
    verify();
    BenchmarkConfig.Builder builder =
        BenchmarkConfig.builder()
            .setName(name)
            .setTarget(BenchmarkTarget.R8)
            .setSuite(BenchmarkSuite.OPENSOURCE_BENCHMARKS)
            .setMethod(runR8(this, configuration))
            .setFromRevision(fromRevision)
            .addDependency(dumpDependency)
            .measureRunTime()
            // TODO(b/373550435): Update dex2oat to enable checking absence of verification errors
            //  on SystemUI.
            .setEnableDex2OatVerification(!name.equals("SystemUIApp"))
            .setTimeout(10, TimeUnit.MINUTES);
    if (!runtimeOnly) {
      builder
          .measureCodeSize()
          .measureInstructionCodeSize()
          .measureComposableInstructionCodeSize()
          .measureDexSegmentsCodeSize()
          .measureDex2OatCodeSize();
      if (enableResourceShrinking) {
        builder.measureResourceSize();
      }
    }
    return builder.build();
  }

  public BenchmarkConfig buildR8WithPartialShrinking() {
    return buildR8WithPartialShrinking(getDefaultR8PartialConfiguration());
  }

  public BenchmarkConfig buildR8WithPartialShrinking(
      ThrowableConsumer<? super R8PartialTestBuilder> configuration) {
    return buildR8WithPartialShrinking(configuration, ThrowableConsumer.empty());
  }

  public BenchmarkConfig buildR8WithPartialShrinking(
      ThrowableConsumer<? super R8PartialTestBuilder> configuration,
      ThrowableConsumer<? super R8PartialTestCompileResult> compileResultConsumer) {
    verify();
    BenchmarkConfig.Builder builder =
        BenchmarkConfig.builder()
            .setName(name)
            .setTarget(BenchmarkTarget.R8)
            .setSuite(BenchmarkSuite.OPENSOURCE_BENCHMARKS)
            .setMethod(runR8WithPartialShrinking(this, configuration, compileResultConsumer))
            .setFromRevision(fromRevision)
            .addDependency(dumpDependency)
            .measureRunTime()
            .measureCodeSize()
            .measureInstructionCodeSize()
            .measureComposableInstructionCodeSize()
            .measureDexSegmentsCodeSize()
            .measureDex2OatCodeSize()
            // TODO(b/373550435): Update dex2oat to enable checking absence of verification errors
            //  on SystemUI.
            .setEnableDex2OatVerification(!name.equals("SystemUIAppPartialShrinking"))
            .setTimeout(10, TimeUnit.MINUTES);
    if (enableResourceShrinking) {
      builder.measureResourceSize();
    }
    return builder.build();
  }

  public BenchmarkConfig buildIncrementalD8() {
    verify();
    if (programPackages.isEmpty()) {
      throw new BenchmarkConfigError(
          "Incremental benchmark should specifiy at least one program package");
    }
    return BenchmarkConfig.builder()
        .setName(name)
        .setTarget(BenchmarkTarget.D8)
        .setSuite(BenchmarkSuite.OPENSOURCE_BENCHMARKS)
        .setMethod(runIncrementalD8(this))
        .setFromRevision(fromRevision)
        .addDependency(dumpDependency)
        .addSubBenchmark(nameForDexPart(), BenchmarkMetric.RunTimeRaw)
        .addSubBenchmark(nameForMergePart(), BenchmarkMetric.RunTimeRaw)
        .setTimeout(10, TimeUnit.MINUTES)
        .build();
  }

  public BenchmarkConfig buildBatchD8() {
    verify();
    return BenchmarkConfig.builder()
        .setName(name)
        .setTarget(BenchmarkTarget.D8)
        .setSuite(BenchmarkSuite.OPENSOURCE_BENCHMARKS)
        .setMethod(runBatchD8(this))
        .setFromRevision(fromRevision)
        .addDependency(dumpDependency)
        .measureRunTime()
        .measureCodeSize()
        .setTimeout(10, TimeUnit.MINUTES)
        .build();
  }

  private String nameForCodePart() {
    return name + "Code";
  }

  private String nameForDexPart() {
    return name + "Dex";
  }

  private String nameForResourcePart() {
    return name + "Resource";
  }

  private String nameForRuntimePart() {
    return name + "Runtime";
  }

  private String nameForMergePart() {
    return name + "Merge";
  }

  private CompilerDump getExtractedDump(BenchmarkEnvironment environment) throws IOException {
    String dumpName = enableResourceShrinking ? "dump_app_res.zip" : "dump_app.zip";
    Path dump = dumpDependency.getRoot(environment).resolve(dumpName);
    return CompilerDump.fromArchive(dump, environment.getTemp().newFolder().toPath());
  }

  private static void addDesugaredLibrary(
      TestCompilerBuilder<?, ?, ?, ?, ?> builder, CompilerDump dump) {
    Path config = dump.getDesugaredLibraryFile();
    if (Files.exists(config)) {
      builder.enableCoreLibraryDesugaring(
          LibraryDesugaringTestConfiguration.forSpecification(config));
    }
  }

  private static ThrowableConsumer<? super R8FullTestBuilder> getDefaultR8Configuration() {
    return testBuilder ->
        testBuilder
            .allowUnnecessaryDontWarnWildcards()
            .allowUnusedDontWarnPatterns()
            .allowUnusedProguardConfigurationRules()
            // TODO(b/222228826): Disallow unrecognized diagnostics and open interfaces.
            .allowDiagnosticMessages()
            .addOptionsModification(
                options -> options.getOpenClosedInterfacesOptions().suppressAllOpenInterfaces());
  }

  private static ThrowableConsumer<? super R8PartialTestBuilder>
      getDefaultR8PartialConfiguration() {
    return testBuilder ->
        testBuilder
            .allowUnnecessaryDontWarnWildcards()
            .allowUnusedDontWarnPatterns()
            .allowUnusedProguardConfigurationRules()
            // TODO(b/222228826): Disallow unrecognized diagnostics and open interfaces.
            .allowDiagnosticMessages()
            .addR8PartialR8OptionsModification(
                options -> options.getOpenClosedInterfacesOptions().suppressAllOpenInterfaces());
  }

  private static ThrowableConsumer<? super R8TestBuilder<?, ?, ?>>
      getKeepComposableAnnotationsConfiguration() {
    return testBuilder ->
        testBuilder
            .addProgramClasses(KeepComposableAnnotations.class)
            .addKeepClassAndMembersRules("androidx.compose.runtime.Composable")
            .addKeepRuntimeVisibleAnnotations()
            .enableExperimentalKeepAnnotations();
  }

  private static BenchmarkMethod runR8(
      AppDumpBenchmarkBuilder builder, ThrowableConsumer<? super R8FullTestBuilder> configuration) {
    return internalRunR8(builder, configuration);
  }

  private static BenchmarkMethod runR8WithPartialShrinking(
      AppDumpBenchmarkBuilder builder,
      ThrowableConsumer<? super R8PartialTestBuilder> configuration,
      ThrowableConsumer<? super R8PartialTestCompileResult> compileResultConsumer) {
    return internalRunR8Partial(builder, configuration, compileResultConsumer);
  }

  private static BenchmarkMethod internalRunR8(
      AppDumpBenchmarkBuilder builder,
      ThrowableConsumer<? super R8FullTestBuilder> configuration) {
    return environment ->
        BenchmarkBase.runner(environment)
            .setWarmupIterations(0)
            .run(
                results -> {
                  CompilerDump dump = builder.getExtractedDump(environment);
                  DumpOptions dumpProperties = dump.getBuildProperties();
                  TestBase.testForR8(environment.getTemp(), Backend.DEX)
                      .addProgramFiles(dump.getProgramArchive())
                      .addLibraryFiles(dump.getLibraryArchive())
                      .addKeepRuleFiles(dump.getProguardConfigFile())
                      .addOptionsModification(
                          options -> {
                            options.apiModelingOptions().androidApiExtensionPackages =
                                dumpProperties.getAndroidApiExtensionPackages();
                            options
                                .horizontalClassMergerOptions()
                                .setEnableSameFilePolicy(dumpProperties.getEnableSameFilePolicy());
                          })
                      .enableIsolatedSplits(dumpProperties.getIsolatedSplits())
                      .setMinApi(dumpProperties.getMinApi())
                      .apply(
                          testBuilder -> {
                            dump.forEachFeatureArchive(testBuilder::addFeatureSplit);
                            addDesugaredLibrary(testBuilder, dump);
                          })
                      .apply(configuration)
                      .applyIf(
                          environment.getConfig().containsComposableCodeSizeMetric(),
                          getKeepComposableAnnotationsConfiguration())
                      .applyIf(
                          builder.enableResourceShrinking,
                          b ->
                              b.enableOptimizedShrinking()
                                  .setAndroidResourcesFromPath(dump.getAndroidResources()))
                      .apply(
                          r -> {
                            try {
                              r.benchmarkCompile(results)
                                  .benchmarkCodeSize(results)
                                  .benchmarkInstructionCodeSize(results)
                                  .benchmarkDexSegmentsCodeSize(results)
                                  .benchmarkDex2OatCodeSize(
                                      results,
                                      environment.getConfig().isDex2OatVerificationEnabled())
                                  .applyIf(
                                      environment
                                          .getConfig()
                                          .containsMetric(BenchmarkMetric.ResourceSize),
                                      cr -> cr.benchmarkResourceSize(results));
                            } catch (CompilationFailedException e) {
                              if (!(e.getCause() instanceof AbortBenchmarkException)) {
                                throw e;
                              }
                            }
                          });
                });
  }

  private static BenchmarkMethod internalRunR8Partial(
      AppDumpBenchmarkBuilder builder,
      ThrowableConsumer<? super R8PartialTestBuilder> configuration,
      ThrowableConsumer<? super R8PartialTestCompileResult> compileResultConsumer) {
    return environment ->
        BenchmarkBase.runner(environment)
            .setWarmupIterations(builder.warmupIterations)
            .run(
                results -> {
                  CompilerDump dump = builder.getExtractedDump(environment);
                  DumpOptions dumpProperties = dump.getBuildProperties();

                  // Run R8.
                  TestBase.testForR8Partial(environment.getTemp())
                      .addProgramFiles(dump.getProgramArchive())
                      .addLibraryFiles(dump.getLibraryArchive())
                      .addKeepRules(
                          // TODO(b/392529669): Add support for proto shrinking.
                          StringUtils.replaceAll(
                              FileUtils.readTextFile(dump.getProguardConfigFile()),
                              "-shrinkunusedprotofields",
                              ""))
                      .addR8PartialR8OptionsModification(
                          options -> {
                            options.apiModelingOptions().androidApiExtensionPackages =
                                dumpProperties.getAndroidApiExtensionPackages();
                            options
                                .horizontalClassMergerOptions()
                                .setEnableSameFilePolicy(dumpProperties.getEnableSameFilePolicy());
                          })
                      .enableIsolatedSplits(dumpProperties.getIsolatedSplits())
                      .setMinApi(dumpProperties.getMinApi())
                      .setR8PartialConfiguration(
                          b -> {
                            if (builder.programPackages.isEmpty()) {
                              b.addJavaTypeIncludePattern("androidx.**");
                              b.addJavaTypeIncludePattern("kotlin.**");
                              b.addJavaTypeIncludePattern("kotlinx.**");
                            } else {
                              builder.programPackages.forEach(b::addJavaTypeIncludePattern);
                            }
                          })
                      .apply(
                          b -> {
                            dump.forEachFeatureArchive(b::addFeatureSplit);
                            addDesugaredLibrary(b, dump);
                          })
                      .apply(configuration)
                      .applyIf(
                          environment.getConfig().containsComposableCodeSizeMetric(),
                          getKeepComposableAnnotationsConfiguration())
                      .applyIf(
                          builder.enableResourceShrinking,
                          b ->
                              b.enableOptimizedShrinking()
                                  .setAndroidResourcesFromPath(dump.getAndroidResources()))
                      .apply(
                          r ->
                              r.benchmarkCompile(results)
                                  .benchmarkCodeSize(results)
                                  .benchmarkInstructionCodeSize(results)
                                  .benchmarkDexSegmentsCodeSize(results)
                                  .benchmarkDex2OatCodeSize(
                                      results,
                                      environment.getConfig().isDex2OatVerificationEnabled())
                                  .applyIf(
                                      environment
                                          .getConfig()
                                          .containsMetric(BenchmarkMetric.ResourceSize),
                                      cr -> cr.benchmarkResourceSize(results))
                                  .apply(compileResultConsumer));
                });
  }

  private static BenchmarkMethod runBatchD8(AppDumpBenchmarkBuilder builder) {
    assert builder.compilationMode != null;
    return environment ->
        BenchmarkBase.runner(environment)
            .setWarmupIterations(builder.warmupIterations)
            .run(
                results -> {
                  CompilerDump dump = builder.getExtractedDump(environment);
                  DumpOptions dumpProperties = dump.getBuildProperties();
                  TestBase.testForD8(environment.getTemp(), Backend.DEX)
                      .addProgramFiles(dump.getProgramArchive())
                      .addLibraryFiles(dump.getLibraryArchive())
                      .setMinApi(dumpProperties.getMinApi())
                      .setMode(builder.compilationMode)
                      .applyIf(builder.enableLibraryDesugaring, b -> addDesugaredLibrary(b, dump))
                      .benchmarkCompile(results)
                      .benchmarkCodeSize(results);
                });
  }

  private static BenchmarkMethod runIncrementalD8(AppDumpBenchmarkBuilder builder) {
    assert builder.compilationMode.isDebug();
    return environment ->
        BenchmarkBase.runner(environment)
            .setWarmupIterations(builder.warmupIterations)
            .reportResultSum()
            .run(
                results -> {
                  CompilerDump dump = builder.getExtractedDump(environment);
                  DumpOptions dumpProperties = dump.getBuildProperties();

                  int numShards = 1;
                  PackageSplitResources resources =
                      PackageSplitResources.create(
                          environment.getTemp(),
                          dump.getProgramArchive(),
                          builder.programPackages,
                          numShards);

                  // Compile all files to a single DEX file.
                  List<List<Path>> compiledShards = new ArrayList<>();
                  BenchmarkResultsSingle dexResults =
                      new BenchmarkResultsSingle(
                          "tmp", ImmutableSet.of(BenchmarkMetric.RunTimeRaw));
                  for (List<Path> shard : resources.getShards()) {
                    List<Path> compiledShard = new ArrayList<>(shard.size());
                    for (Path programFile : shard) {
                      TestBase.testForD8(environment.getTemp())
                          .addProgramFiles(programFile)
                          .addClasspathFiles(dump.getProgramArchive())
                          .addLibraryFiles(dump.getLibraryArchive())
                          .applyIf(
                              builder.enableLibraryDesugaring, b -> addDesugaredLibrary(b, dump))
                          .debug()
                          .setIntermediate(true)
                          .setMinApi(dumpProperties.getMinApi())
                          .benchmarkCompile(dexResults)
                          .writeToZip(compiledShard::add);
                    }
                    compiledShards.add(compiledShard);
                  }
                  dexResults.doAverage();
                  results
                      .getSubResults(builder.nameForDexPart())
                      .addRuntimeResult(dexResults.getRuntimeResults().getLong(0));

                  // Merge each compiled shard.
                  for (List<Path> compiledShard : compiledShards) {
                    TestBase.testForD8(environment.getTemp())
                        .addProgramFiles(compiledShard)
                        .addLibraryFiles(dump.getLibraryArchive())
                        .debug()
                        .setMinApi(dumpProperties.getMinApi())
                        .benchmarkCompile(results.getSubResults(builder.nameForMergePart()));
                  }
                });
  }
}
