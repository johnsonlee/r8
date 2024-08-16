// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks;

import com.android.tools.r8.errors.Unimplemented;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.io.PrintStream;

public class BenchmarkResultsWarmup implements BenchmarkResults {

  private final String name;
  private final LongList runtimeResults = new LongArrayList();
  private long codeSizeResult = -1;
  private long composableCodeSizeResult = -1;
  private long resourceSizeResult = -1;

  public BenchmarkResultsWarmup(String name) {
    this.name = name;
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
  public void addComposableCodeSizeResult(long result) {
    if (composableCodeSizeResult == -1) {
      composableCodeSizeResult = result;
    }
    if (composableCodeSizeResult != result) {
      throw new RuntimeException(
          "Unexpected Composable code size difference: "
              + result
              + " and "
              + composableCodeSizeResult);
    }
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
  public BenchmarkResults getSubResults(String name) {
    // When running warmups all results are amended to the single warmup result.
    return this;
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
  public void writeResults(PrintStream out) {
    throw new Unimplemented();
  }
}
