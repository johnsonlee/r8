// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks;

import static java.util.Collections.emptyList;

import com.android.tools.r8.benchmarks.appdumps.ChromeBenchmarks;
import com.android.tools.r8.benchmarks.appdumps.ComposeSamplesBenchmarks;
import com.android.tools.r8.benchmarks.appdumps.NowInAndroidBenchmarks;
import com.android.tools.r8.benchmarks.appdumps.TiviBenchmarks;
import com.android.tools.r8.benchmarks.desugaredlib.L8Benchmark;
import com.android.tools.r8.benchmarks.helloworld.HelloWorldBenchmark;
import com.android.tools.r8.benchmarks.retrace.RetraceStackTraceBenchmark;
import com.android.tools.r8.internal.benchmarks.appdumps.AGSABenchmarks;
import com.android.tools.r8.internal.benchmarks.appdumps.SystemUIBenchmarks;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BenchmarkCollection {

  // Actual list of all configured benchmarks.
  private final Map<String, List<BenchmarkConfig>> benchmarks = new HashMap<>();

  @SafeVarargs
  public BenchmarkCollection(List<BenchmarkConfig>... benchmarksCollection) {
    for (List<BenchmarkConfig> benchmarks : benchmarksCollection) {
      benchmarks.forEach(this::addBenchmark);
    }
  }

  private void addBenchmark(BenchmarkConfig benchmark) {
    List<BenchmarkConfig> variants =
        benchmarks.computeIfAbsent(benchmark.getName(), k -> new ArrayList<>());
    for (BenchmarkConfig variant : variants) {
      BenchmarkConfig.checkBenchmarkConsistency(benchmark, variant);
    }
    variants.add(benchmark);
  }

  public List<BenchmarkIdentifier> getBenchmarkIdentifiers() {
    return benchmarks.values().stream()
        .flatMap(cs -> cs.stream().map(BenchmarkConfig::getIdentifier))
        .sorted()
        .collect(Collectors.toList());
  }

  public BenchmarkConfig getBenchmark(BenchmarkIdentifier identifier) {
    assert identifier != null;
    List<BenchmarkConfig> configs = benchmarks.getOrDefault(identifier.getName(), emptyList());
    for (BenchmarkConfig config : configs) {
      if (identifier.equals(config.getIdentifier())) {
        return config;
      }
    }
    return null;
  }

  public static BenchmarkCollection computeCollection() {
    // Every benchmark that should be active on golem must be setup in this method.
    return new BenchmarkCollection(
        AGSABenchmarks.configs(),
        HelloWorldBenchmark.configs(),
        L8Benchmark.configs(),
        NowInAndroidBenchmarks.configs(),
        TiviBenchmarks.configs(),
        RetraceStackTraceBenchmark.configs(),
        ComposeSamplesBenchmarks.configs(),
        ChromeBenchmarks.configs(),
        SystemUIBenchmarks.configs());
  }

  /** Compute and print the golem configuration. */
  public static void main(String[] args) throws IOException {
    new BenchmarkCollectionPrinter(System.out)
        .printGolemConfig(
            computeCollection().benchmarks.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList()));
  }
}
