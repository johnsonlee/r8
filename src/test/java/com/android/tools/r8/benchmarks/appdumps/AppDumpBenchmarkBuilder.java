// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks.appdumps;


import com.android.tools.r8.LibraryDesugaringTestConfiguration;
import com.android.tools.r8.R8FullTestBuilder;
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
  private boolean enableLibraryDesugaring = true;
  private final List<String> programPackages = new ArrayList<>();

  public void verify() {
    if (name == null) {
      throw new BenchmarkConfigError("Missing name");
    }
    if (dumpDependency == null) {
      throw new BenchmarkConfigError("Missing dump");
    }
    if (fromRevision < 0) {
      throw new BenchmarkConfigError("Missing from-revision");
    }
  }

  public AppDumpBenchmarkBuilder setEnableLibraryDesugaring(boolean enableLibraryDesugaring) {
    this.enableLibraryDesugaring = enableLibraryDesugaring;
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
    return BenchmarkConfig.builder()
        .setName(name)
        .setTarget(BenchmarkTarget.R8)
        .setSuite(BenchmarkSuite.OPENSOURCE_BENCHMARKS)
        .setMethod(runR8(this, configuration))
        .setFromRevision(fromRevision)
        .addDependency(dumpDependency)
        .measureRunTime()
        .measureCodeSize()
        .measureInstructionCodeSize()
        .measureComposableInstructionCodeSize()
        .measureDexSegmentsCodeSize()
        .measureDex2OatCodeSize()
        .setTimeout(10, TimeUnit.MINUTES)
        .build();
  }

  public BenchmarkConfig buildR8WithResourceShrinking() {
    return buildR8WithResourceShrinking(getDefaultR8Configuration());
  }

  public BenchmarkConfig buildR8WithResourceShrinking(
      ThrowableConsumer<? super R8FullTestBuilder> configuration) {
    verify();
    return BenchmarkConfig.builder()
        .setName(name)
        .setTarget(BenchmarkTarget.R8)
        .setSuite(BenchmarkSuite.OPENSOURCE_BENCHMARKS)
        .setMethod(runR8WithResourceShrinking(this, configuration))
        .setFromRevision(fromRevision)
        .addDependency(dumpDependency)
        .measureRunTime()
        .setTimeout(10, TimeUnit.MINUTES)
        .build();
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
    return getExtractedDump(environment, false);
  }

  private CompilerDump getExtractedDump(
      BenchmarkEnvironment environment, boolean enableResourceShrinking) throws IOException {
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

  private static ThrowableConsumer<? super R8FullTestBuilder>
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
    return internalRunR8(builder, false, configuration);
  }

  private static BenchmarkMethod runR8WithResourceShrinking(
      AppDumpBenchmarkBuilder builder, ThrowableConsumer<? super R8FullTestBuilder> configuration) {
    return internalRunR8(builder, true, configuration);
  }

  private static BenchmarkMethod internalRunR8(
      AppDumpBenchmarkBuilder builder,
      boolean enableResourceShrinking,
      ThrowableConsumer<? super R8FullTestBuilder> configuration) {
    return environment ->
        BenchmarkBase.runner(environment)
            .setWarmupIterations(1)
            .run(
                results -> {
                  CompilerDump dump =
                      builder.getExtractedDump(environment, enableResourceShrinking);
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
                          enableResourceShrinking,
                          b ->
                              b.enableOptimizedShrinking()
                                  .setAndroidResourcesFromPath(dump.getAndroidResources()))
                      .applyIf(
                          enableResourceShrinking,
                          r -> {
                            try {
                              r.benchmarkCompile(results);
                            } catch (Exception e) {
                              // Ignore.
                            }
                          },
                          r ->
                              r.benchmarkCompile(results)
                                  .benchmarkCodeSize(results)
                                  .benchmarkInstructionCodeSize(results)
                                  .benchmarkDexSegmentsCodeSize(results)
                                  .benchmarkDex2OatCodeSize(results));
                });
  }

  private static BenchmarkMethod runBatchD8(AppDumpBenchmarkBuilder builder) {
    return environment ->
        BenchmarkBase.runner(environment)
            .setWarmupIterations(1)
            .run(
                results -> {
                  CompilerDump dump = builder.getExtractedDump(environment);
                  DumpOptions dumpProperties = dump.getBuildProperties();
                  TestBase.testForD8(environment.getTemp(), Backend.DEX)
                      .addProgramFiles(dump.getProgramArchive())
                      .addLibraryFiles(dump.getLibraryArchive())
                      .setMinApi(dumpProperties.getMinApi())
                      .applyIf(builder.enableLibraryDesugaring, b -> addDesugaredLibrary(b, dump))
                      .benchmarkCompile(results)
                      .benchmarkCodeSize(results);
                });
  }

  private static BenchmarkMethod runIncrementalD8(AppDumpBenchmarkBuilder builder) {
    return environment ->
        BenchmarkBase.runner(environment)
            .setWarmupIterations(1)
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
