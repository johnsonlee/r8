// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks;

import com.android.tools.r8.DexSegments.SegmentInfo;
import com.android.tools.r8.dex.DexSection;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.IntToLongFunction;
import java.util.function.LongConsumer;

public class BenchmarkResultsSingle implements BenchmarkResults {

  private final String name;
  private final Set<BenchmarkMetric> metrics;
  private final LongList runtimeResults = new LongArrayList();

  // Consider using LongSet to eliminate duplicate results for size.
  private final LongList codeSizeResults = new LongArrayList();
  private final LongList instructionCodeSizeResults = new LongArrayList();
  private final LongList composableInstructionCodeSizeResults = new LongArrayList();
  private final LongList dex2OatSizeResult = new LongArrayList();
  private final List<Int2ReferenceMap<SegmentInfo>> dexSegmentsSizeResults = new ArrayList<>();

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

  public LongList getInstructionCodeSizeResults() {
    return instructionCodeSizeResults;
  }

  public LongList getComposableInstructionCodeSizeResults() {
    return composableInstructionCodeSizeResults;
  }

  public List<Int2ReferenceMap<SegmentInfo>> getDexSegmentsSizeResults() {
    return dexSegmentsSizeResults;
  }

  public LongList getDex2OatSizeResult() {
    return dex2OatSizeResult;
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
  public void addInstructionCodeSizeResult(long result) {
    verifyMetric(
        BenchmarkMetric.InstructionCodeSize,
        metrics.contains(BenchmarkMetric.InstructionCodeSize),
        true);
    instructionCodeSizeResults.add(result);
  }

  @Override
  public void addComposableInstructionCodeSizeResult(long result) {
    verifyMetric(
        BenchmarkMetric.ComposableInstructionCodeSize,
        metrics.contains(BenchmarkMetric.ComposableInstructionCodeSize),
        true);
    composableInstructionCodeSizeResults.add(result);
  }

  @Override
  public void addDexSegmentsSizeResult(Int2ReferenceMap<SegmentInfo> result) {
    verifyMetric(
        BenchmarkMetric.DexSegmentsCodeSize,
        metrics.contains(BenchmarkMetric.DexSegmentsCodeSize),
        true);
    dexSegmentsSizeResults.add(result);
  }

  @Override
  public void addDex2OatSizeResult(long result) {
    verifyMetric(
        BenchmarkMetric.Dex2OatCodeSize, metrics.contains(BenchmarkMetric.Dex2OatCodeSize), true);
    dex2OatSizeResult.add(result);
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
        isBenchmarkingCodeSize() && metrics.contains(BenchmarkMetric.CodeSize),
        !codeSizeResults.isEmpty());
    verifyMetric(
        BenchmarkMetric.InstructionCodeSize,
        isBenchmarkingCodeSize() && metrics.contains(BenchmarkMetric.InstructionCodeSize),
        !instructionCodeSizeResults.isEmpty());
    verifyMetric(
        BenchmarkMetric.ComposableInstructionCodeSize,
        isBenchmarkingCodeSize() && metrics.contains(BenchmarkMetric.ComposableInstructionCodeSize),
        !composableInstructionCodeSizeResults.isEmpty());
    verifyMetric(
        BenchmarkMetric.DexSegmentsCodeSize,
        isBenchmarkingCodeSize() && metrics.contains(BenchmarkMetric.DexSegmentsCodeSize),
        !dexSegmentsSizeResults.isEmpty());
    verifyMetric(
        BenchmarkMetric.Dex2OatCodeSize,
        isBenchmarkingCodeSize() && metrics.contains(BenchmarkMetric.Dex2OatCodeSize),
        !dex2OatSizeResult.isEmpty());
  }

  private void printRunTime(long duration) {
    String value = BenchmarkResults.prettyTime(duration);
    System.out.println(BenchmarkResults.prettyMetric(name, BenchmarkMetric.RunTimeRaw, value));
  }

  private void printCodeSize(long bytes) {
    System.out.println(BenchmarkResults.prettyMetric(name, BenchmarkMetric.CodeSize, bytes));
  }

  private void printInstructionCodeSize(long bytes) {
    System.out.println(
        BenchmarkResults.prettyMetric(name, BenchmarkMetric.InstructionCodeSize, bytes));
  }

  private void printComposableInstructionCodeSize(long bytes) {
    System.out.println(
        BenchmarkResults.prettyMetric(name, BenchmarkMetric.ComposableInstructionCodeSize, bytes));
  }

  private void printDexSegmentSize(int section, long bytes) {
    System.out.println(
        BenchmarkResults.prettyMetric(
            name,
            BenchmarkMetric.DexSegmentsCodeSize + ", " + DexSection.typeName(section),
            bytes));
  }

  private void printDex2OatSize(long bytes) {
    System.out.println(BenchmarkResults.prettyMetric(name, BenchmarkMetric.Dex2OatCodeSize, bytes));
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
        instructionCodeSizeResults, failOnCodeSizeDifferences, this::printInstructionCodeSize);
    printCodeSizeResults(
        composableInstructionCodeSizeResults,
        failOnCodeSizeDifferences,
        this::printComposableInstructionCodeSize);
    for (int section : DexSection.getConstants()) {
      printCodeSizeResults(
          dexSegmentsSizeResults,
          i -> dexSegmentsSizeResults.get(i).get(section).getSegmentSize(),
          failOnCodeSizeDifferences,
          result -> printDexSegmentSize(section, result));
    }
    printCodeSizeResults(dex2OatSizeResult, failOnCodeSizeDifferences, this::printDex2OatSize);
  }

  private static void printCodeSizeResults(
      LongList codeSizeResults, boolean failOnCodeSizeDifferences, LongConsumer printer) {
    printCodeSizeResults(
        codeSizeResults, codeSizeResults::getLong, failOnCodeSizeDifferences, printer);
  }

  private static void printCodeSizeResults(
      Collection<?> codeSizeResults,
      IntToLongFunction getter,
      boolean failOnCodeSizeDifferences,
      LongConsumer printer) {
    if (codeSizeResults.isEmpty()) {
      return;
    }
    long size = getter.applyAsLong(0);
    if (failOnCodeSizeDifferences) {
      for (int i = 1; i < codeSizeResults.size(); i++) {
        if (size != getter.applyAsLong(i)) {
          throw new RuntimeException(
              "Unexpected code size difference: " + size + " and " + getter.applyAsLong(i));
        }
      }
    }
    printer.accept(size);
  }

  public int size() {
    return runtimeResults.size();
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
