// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks;

import com.android.tools.r8.utils.ListUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;

public class BenchmarkResultsSingleAdapter implements JsonSerializer<BenchmarkResultsSingle> {

  @Override
  public JsonElement serialize(
      BenchmarkResultsSingle result, Type type, JsonSerializationContext jsonSerializationContext) {
    JsonArray resultsArray = new JsonArray();
    ListUtils.forEachWithIndex(
        result.getCodeSizeResults(),
        (codeSizeResult, iteration) -> {
          JsonObject resultObject = new JsonObject();
          resultObject.addProperty("code_size", codeSizeResult);
          if (!result.getComposableCodeSizeResults().isEmpty()) {
            resultObject.addProperty(
                "composable_code_size", result.getComposableCodeSizeResults().getLong(iteration));
          }
          resultObject.addProperty("runtime", result.getRuntimeResults().getLong(iteration));
          resultsArray.add(resultObject);
        });

    JsonObject benchmarkObject = new JsonObject();
    benchmarkObject.addProperty("benchmark_name", result.getName());
    benchmarkObject.add("results", resultsArray);
    return benchmarkObject;
  }
}
