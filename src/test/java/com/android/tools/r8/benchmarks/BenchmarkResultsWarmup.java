// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks;

import static org.junit.Assert.assertFalse;

import com.android.tools.r8.DexSegments.SegmentInfo;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.errors.Unreachable;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class BenchmarkResultsWarmup implements BenchmarkResults {

  private final String name;
  private final LongList runtimeResults = new LongArrayList();
  private long codeSizeResult = -1;
  private long resourceSizeResult = -1;

  private final Map<String, BenchmarkResultsWarmup> results = new HashMap<>();

  public BenchmarkResultsWarmup(String name) {
    this.name = name;
  }

  public BenchmarkResultsWarmup(String name, Map<String, Set<BenchmarkMetric>> benchmarks) {
    this.name = name;
    benchmarks.forEach(
        (benchmarkName, metrics) ->
            results.put(benchmarkName, new BenchmarkResultsWarmup(benchmarkName)));
  }

  @Override
  public void addRuntimeResult(long result) {
    runtimeResults.add(result);
  }

  @Override
  public void addCodeSizeResult(long result) {
    if (codeSizeResult == -1) {
      codeSizeResult = result;
    }
    if (codeSizeResult != result) {
      throw new RuntimeException(
          "Unexpected code size difference: " + result + " and " + codeSizeResult);
    }
  }

  @Override
  public void addInstructionCodeSizeResult(long result) {
    throw addSizeResultError();
  }

  @Override
  public void addComposableInstructionCodeSizeResult(long result) {
    throw addSizeResultError();
  }

  @Override
  public void addDexSegmentsSizeResult(Int2ReferenceMap<SegmentInfo> result) {
    throw addSizeResultError();
  }

  @Override
  public void addDex2OatSizeResult(long result) {
    throw addSizeResultError();
  }

  private Unreachable addSizeResultError() {
    throw new Unreachable("Unexpected attempt to add size result for warmup run");
  }

  @Override
  public void addResourceSizeResult(long result) {
    if (resourceSizeResult == -1) {
      resourceSizeResult = result;
    }
    if (resourceSizeResult != result) {
      throw new RuntimeException(
          "Unexpected resource size difference: " + result + " and " + resourceSizeResult);
    }
  }

  @Override
  public void doAverage() {
    assertFalse(runtimeResults.isEmpty());
    long averageRuntimeResult =
        Math.round(runtimeResults.stream().mapToLong(Long::longValue).average().orElse(0));
    runtimeResults.clear();
    addRuntimeResult(averageRuntimeResult);
  }

  public LongList getRuntimeResults() {
    return runtimeResults;
  }

  @Override
  public BenchmarkResults getSubResults(String name) {
    return results.get(name);
  }

  @Override
  public boolean isBenchmarkingCodeSize() {
    return false;
  }

  public int size() {
    return runtimeResults.size();
  }

  @Override
  public void printResults(ResultMode mode, boolean failOnCodeSizeDifferences) {
    if (runtimeResults.isEmpty()) {
      throw new BenchmarkConfigError("Expected runtime results for warmup run");
    }
    long sum = runtimeResults.stream().mapToLong(l -> l).sum();
    long result = mode == ResultMode.SUM ? sum : sum / runtimeResults.size();
    System.out.println(
        BenchmarkResults.prettyMetric(
            name, BenchmarkMetric.StartupTime, BenchmarkResults.prettyTime(result)));
  }

  @Override
  public void writeResults(Path path, BenchmarkResults warmupResults) {
    throw new Unimplemented();
  }
}
