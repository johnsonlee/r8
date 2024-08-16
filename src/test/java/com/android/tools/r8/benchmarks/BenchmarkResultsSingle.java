// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.io.PrintStream;
import java.util.Set;
import java.util.function.LongConsumer;

public class BenchmarkResultsSingle implements BenchmarkResults {

  private final String name;
  private final Set<BenchmarkMetric> metrics;
  private final LongList runtimeResults = new LongArrayList();
  private final LongList codeSizeResults = new LongArrayList();
  private final LongList composableCodeSizeResults = new LongArrayList();

  public BenchmarkResultsSingle(String name, Set<BenchmarkMetric> metrics) {
    this.name = name;
    this.metrics = metrics;
  }

  public String getName() {
    return name;
  }

  public LongList getCodeSizeResults() {
    return codeSizeResults;
  }

  public LongList getComposableCodeSizeResults() {
    return composableCodeSizeResults;
  }

  public LongList getRuntimeResults() {
    return runtimeResults;
  }

  @Override
  public void addRuntimeResult(long result) {
    verifyMetric(BenchmarkMetric.RunTimeRaw, metrics.contains(BenchmarkMetric.RunTimeRaw), true);
    runtimeResults.add(result);
  }

  @Override
  public void addCodeSizeResult(long result) {
    verifyMetric(BenchmarkMetric.CodeSize, metrics.contains(BenchmarkMetric.CodeSize), true);
    codeSizeResults.add(result);
  }

  @Override
  public void addComposableCodeSizeResult(long result) {
    verifyMetric(
        BenchmarkMetric.ComposableCodeSize,
        metrics.contains(BenchmarkMetric.ComposableCodeSize),
        true);
    composableCodeSizeResults.add(result);
  }

  @Override
  public void addResourceSizeResult(long result) {
    addCodeSizeResult(result);
  }

  @Override
  public BenchmarkResults getSubResults(String name) {
    throw new BenchmarkConfigError(
        "Unexpected attempt to get sub-results for benchmark without sub-benchmarks");
  }

  private static void verifyMetric(BenchmarkMetric metric, boolean expected, boolean actual) {
    if (expected != actual) {
      throw new BenchmarkConfigError(
          "Mismatched config and result for "
              + metric.name()
              + ". Expected by config: "
              + expected
              + ", but has result: "
              + actual);
    }
  }

  private void verifyConfigAndResults() {
    verifyMetric(
        BenchmarkMetric.RunTimeRaw,
        metrics.contains(BenchmarkMetric.RunTimeRaw),
        !runtimeResults.isEmpty());
    verifyMetric(
        BenchmarkMetric.CodeSize,
        metrics.contains(BenchmarkMetric.CodeSize),
        !codeSizeResults.isEmpty());
    verifyMetric(
        BenchmarkMetric.ComposableCodeSize,
        metrics.contains(BenchmarkMetric.ComposableCodeSize),
        !composableCodeSizeResults.isEmpty());
  }

  private void printRunTime(long duration) {
    String value = BenchmarkResults.prettyTime(duration);
    System.out.println(BenchmarkResults.prettyMetric(name, BenchmarkMetric.RunTimeRaw, value));
  }

  private void printCodeSize(long bytes) {
    System.out.println(BenchmarkResults.prettyMetric(name, BenchmarkMetric.CodeSize, "" + bytes));
  }

  private void printComposableCodeSize(long bytes) {
    System.out.println(
        BenchmarkResults.prettyMetric(name, BenchmarkMetric.ComposableCodeSize, "" + bytes));
  }

  @Override
  public void printResults(ResultMode mode, boolean failOnCodeSizeDifferences) {
    verifyConfigAndResults();
    if (!runtimeResults.isEmpty()) {
      long sum = runtimeResults.stream().mapToLong(l -> l).sum();
      long result = mode == ResultMode.SUM ? sum : sum / runtimeResults.size();
      printRunTime(result);
    }
    printCodeSizeResults(codeSizeResults, failOnCodeSizeDifferences, this::printCodeSize);
    printCodeSizeResults(
        composableCodeSizeResults, failOnCodeSizeDifferences, this::printComposableCodeSize);
  }

  private static void printCodeSizeResults(
      LongList codeSizeResults, boolean failOnCodeSizeDifferences, LongConsumer printer) {
    if (!codeSizeResults.isEmpty()) {
      long size = codeSizeResults.getLong(0);
      if (failOnCodeSizeDifferences) {
        for (int i = 1; i < codeSizeResults.size(); i++) {
          if (size != codeSizeResults.getLong(i)) {
            throw new RuntimeException(
                "Unexpected code size difference: " + size + " and " + codeSizeResults.getLong(i));
          }
        }
      }
      printer.accept(size);
    }
  }

  @Override
  public void writeResults(PrintStream out) {
    Gson gson =
        new GsonBuilder()
            .registerTypeAdapter(BenchmarkResultsSingle.class, new BenchmarkResultsSingleAdapter())
            .create();
    out.print(gson.toJson(this));
  }
}
