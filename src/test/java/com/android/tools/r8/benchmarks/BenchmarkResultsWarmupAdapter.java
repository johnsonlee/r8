// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;

public class BenchmarkResultsWarmupAdapter extends BenchmarkResultsAdapterBase
    implements JsonSerializer<BenchmarkResultsWarmup> {

  @Override
  public JsonElement serialize(
      BenchmarkResultsWarmup result, Type type, JsonSerializationContext jsonSerializationContext) {
    JsonArray resultsArray = new JsonArray();
    for (int iteration = 0; iteration < result.size(); iteration++) {
      JsonObject resultObject = new JsonObject();
      addPropertyIfValueDifferentFromRepresentative(
          resultObject,
          "runtime",
          iteration,
          result.getRuntimeResults(),
          i -> result.getRuntimeResults().getLong(i));
      resultsArray.add(resultObject);
    }
    return resultsArray;
  }
}
