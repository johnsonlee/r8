// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks;

import com.android.tools.r8.DexSegments.SegmentInfo;
import com.android.tools.r8.utils.StringUtils;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import java.io.IOException;
import java.nio.file.Path;

public interface BenchmarkResults {

  // Append a runtime result. This may be summed or averaged depending on the benchmark set up.
  void addRuntimeResult(long result);

  // Append a code size result. This is always assumed to be identical if called multiple times.
  void addCodeSizeResult(long result);

  void addInstructionCodeSizeResult(long result);

  void addComposableInstructionCodeSizeResult(long result);

  void addDexSegmentsSizeResult(Int2ReferenceMap<SegmentInfo> result);

  void addDex2OatSizeResult(long result);

  // Append a resource size result. This is always assumed to be identical if called multiple times.
  void addResourceSizeResult(long result);

  void doAverage();

  // Get the results collection for a "sub-benchmark" when defining a group of benchmarks.
  // This will throw if called on a benchmark without sub-benchmarks.
  BenchmarkResults getSubResults(String name);

  default boolean isBenchmarkingCodeSize() {
    return true;
  }

  void printResults(ResultMode resultMode, boolean failOnCodeSizeDifferences);

  void writeResults(Path path) throws IOException;

  static String prettyTime(long nanoTime) {
    return "" + (nanoTime / 1000000) + " ms";
  }

  static String prettyMetric(String name, BenchmarkMetric metric, long value) {
    return prettyMetric(name, metric.name(), Long.toString(value));
  }

  static String prettyMetric(String name, BenchmarkMetric metric, String value) {
    return prettyMetric(name, metric.name(), value);
  }

  static String prettyMetric(String name, String metricName, long value) {
    return prettyMetric(name, metricName, Long.toString(value));
  }

  static String prettyMetric(String name, String metricName, String value) {
    return name + "(" + metricName + "): " + value;
  }

  enum ResultMode {
    AVERAGE,
    SUM;

    @Override
    public String toString() {
      return StringUtils.toLowerCase(name());
    }
  }
}
