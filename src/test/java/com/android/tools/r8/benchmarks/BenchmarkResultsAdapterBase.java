// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks;

import com.google.gson.JsonObject;
import java.util.Collection;
import java.util.function.IntToLongFunction;

public abstract class BenchmarkResultsAdapterBase {

  void addPropertyIfValueDifferentFromRepresentative(
      JsonObject resultObject,
      String propertyName,
      int iteration,
      Collection<?> results,
      IntToLongFunction getter) {
    if (results.isEmpty()) {
      return;
    }
    long result = getter.applyAsLong(iteration);
    if (iteration != 0) {
      long representativeResult = getter.applyAsLong(0);
      if (result == representativeResult) {
        return;
      }
    }
    resultObject.addProperty(propertyName, result);
  }
}
