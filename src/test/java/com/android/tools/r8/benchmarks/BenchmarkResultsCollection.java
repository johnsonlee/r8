// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks;

import com.android.tools.r8.DexSegments.SegmentInfo;
import com.android.tools.r8.errors.Unimplemented;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class BenchmarkResultsCollection implements BenchmarkResults {

  private final Map<String, BenchmarkResultsSingle> results;

  public BenchmarkResultsCollection(Map<String, Set<BenchmarkMetric>> benchmarks) {
    results = new HashMap<>(benchmarks.size());
    benchmarks.forEach(
        (name, metrics) -> results.put(name, new BenchmarkResultsSingle(name, metrics)));
  }

  @Override
  public void addRuntimeResult(long result) {
    throw error();
  }

  @Override
  public void addCodeSizeResult(long result) {
    throw error();
  }

  @Override
  public void addInstructionCodeSizeResult(long result) {
    throw error();
  }

  @Override
  public void addComposableInstructionCodeSizeResult(long result) {
    throw error();
  }

  @Override
  public void addDexSegmentsSizeResult(Int2ReferenceMap<SegmentInfo> result) {
    throw error();
  }

  @Override
  public void addDex2OatSizeResult(long result) {
    throw error();
  }

  @Override
  public void addResourceSizeResult(long result) {
    throw error();
  }

  @Override
  public void doAverage() {
    throw new Unimplemented();
  }

  private BenchmarkConfigError error() {
    throw new BenchmarkConfigError(
        "Unexpected attempt to add a result to a the root of a benchmark with sub-benchmarks");
  }

  @Override
  public BenchmarkResults getSubResults(String name) {
    return results.get(name);
  }

  @Override
  public void printResults(ResultMode mode, boolean failOnCodeSizeDifferences) {
    List<String> sorted = new ArrayList<>(results.keySet());
    sorted.sort(String::compareTo);
    for (String name : sorted) {
      BenchmarkResultsSingle singleResults = results.get(name);
      singleResults.printResults(mode, failOnCodeSizeDifferences);
    }
  }

  @Override
  public void writeResults(Path path, BenchmarkResults warmupResults) throws IOException {
    for (Entry<String, BenchmarkResultsSingle> entry : results.entrySet()) {
      String name = entry.getKey();
      BenchmarkResultsSingle result = entry.getValue();
      BenchmarkResults warmupSubResults =
          warmupResults != null ? warmupResults.getSubResults(name) : null;
      result.writeResults(path.resolve(name), warmupSubResults);
    }
  }
}
