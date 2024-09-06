// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks;

import com.android.tools.r8.benchmarks.BenchmarkResults.ResultMode;

public class BenchmarkRunner {

  public interface BenchmarkRunnerFunction {
    void run(BenchmarkResults results) throws Exception;
  }

  private final BenchmarkEnvironment environment;
  private int warmups = 0;
  private int iterations = 1;
  private ResultMode resultMode = BenchmarkResults.ResultMode.AVERAGE;

  private BenchmarkRunner(BenchmarkEnvironment environment) {
    this.environment = environment;
  }

  public static BenchmarkRunner runner(BenchmarkEnvironment environment) {
    return new BenchmarkRunner(environment);
  }

  public BenchmarkRunner setWarmupIterations(int iterations) {
    this.warmups = iterations;
    return this;
  }

  public int getBenchmarkIterations() {
    return environment.hasBenchmarkIterationsOverride()
        ? environment.getBenchmarkIterationsOverride()
        : iterations;
  }

  public BenchmarkRunner setBenchmarkIterations(int iterations) {
    this.iterations = iterations;
    return this;
  }

  public BenchmarkRunner reportResultAverage() {
    resultMode = BenchmarkResults.ResultMode.AVERAGE;
    return this;
  }

  public BenchmarkRunner reportResultSum() {
    resultMode = BenchmarkResults.ResultMode.SUM;
    return this;
  }

  public void run(BenchmarkRunnerFunction fn) throws Exception {
    long warmupTotalTime = 0;
    BenchmarkConfig config = environment.getConfig();
    BenchmarkResults warmupResults = new BenchmarkResultsWarmup(config.getName());
    if (warmups > 0) {
      long start = System.nanoTime();
      for (int i = 0; i < warmups; i++) {
        fn.run(warmupResults);
      }
      warmupTotalTime = System.nanoTime() - start;
    }
    BenchmarkResults results =
        config.isSingleBenchmark()
            ? new BenchmarkResultsSingle(config.getName(), config.getMetrics())
            : new BenchmarkResultsCollection(config.getSubBenchmarks());
    long start = System.nanoTime();
    for (int i = 0; i < getBenchmarkIterations(); i++) {
      fn.run(results);
    }
    long benchmarkTotalTime = System.nanoTime() - start;
    System.out.println(
        "Benchmark results for "
            + config.getName()
            + " on target "
            + config.getTarget().getIdentifierName());
    if (warmups > 0) {
      printMetaInfo("warmup", warmups, warmupTotalTime);
      if (config.hasTimeWarmupRuns()) {
        warmupResults.printResults(resultMode, environment.failOnCodeSizeDifferences());
      }
    }
    printMetaInfo("benchmark", getBenchmarkIterations(), benchmarkTotalTime);
    results.printResults(resultMode, environment.failOnCodeSizeDifferences());
    if (environment.hasOutputPath()) {
      results.writeResults(environment.getOutputPath());
    }
    System.out.println();
  }

  private void printMetaInfo(String kind, int iterations, long totalTime) {
    System.out.println("  " + kind + " reporting mode: " + resultMode);
    System.out.println("  " + kind + " iterations: " + iterations);
    System.out.println("  " + kind + " total time: " + BenchmarkResults.prettyTime(totalTime));
  }
}
