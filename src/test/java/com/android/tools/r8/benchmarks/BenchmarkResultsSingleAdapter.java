// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks;

import com.android.tools.r8.dex.DexSection;
import com.google.common.base.CaseFormat;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;

public class BenchmarkResultsSingleAdapter extends BenchmarkResultsAdapterBase
    implements JsonSerializer<BenchmarkResultsSingle> {

  @Override
  public JsonElement serialize(
      BenchmarkResultsSingle result, Type type, JsonSerializationContext jsonSerializationContext) {
    JsonArray resultsArray = new JsonArray();
    for (int iteration = 0; iteration < result.size(); iteration++) {
      JsonObject resultObject = new JsonObject();
      addPropertyIfValueDifferentFromRepresentative(
          resultObject,
          "code_size",
          iteration,
          result.getCodeSizeResults(),
          i -> result.getCodeSizeResults().getLong(i));
      addPropertyIfValueDifferentFromRepresentative(
          resultObject,
          "ins_code_size",
          iteration,
          result.getInstructionCodeSizeResults(),
          i -> result.getInstructionCodeSizeResults().getLong(i));
      addPropertyIfValueDifferentFromRepresentative(
          resultObject,
          "composable_code_size",
          iteration,
          result.getComposableInstructionCodeSizeResults(),
          i -> result.getComposableInstructionCodeSizeResults().getLong(i));
      for (int section : DexSection.getConstants()) {
        String sectionName = DexSection.typeName(section);
        String sectionNameUnderscore =
            CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, sectionName);
        addPropertyIfValueDifferentFromRepresentative(
            resultObject,
            "dex_" + sectionNameUnderscore + "_size",
            iteration,
            result.getDexSegmentsSizeResults(),
            i -> result.getDexSegmentsSizeResults().get(i).get(section).getSegmentSize());
      }
      addPropertyIfValueDifferentFromRepresentative(
          resultObject,
          "oat_code_size",
          iteration,
          result.getDex2OatSizeResults(),
          i -> result.getDex2OatSizeResults().getLong(i));
      addPropertyIfValueDifferentFromRepresentative(
          resultObject,
          "runtime",
          iteration,
          result.getRuntimeResults(),
          i -> result.getRuntimeResults().getLong(i));
          resultsArray.add(resultObject);
      addPropertyIfValueDifferentFromRepresentative(
          resultObject,
          "resource_size",
          iteration,
          result.getResourceSizeResults(),
          i -> result.getResourceSizeResults().getLong(i));
      resultsArray.add(resultObject);
    }

    JsonObject benchmarkObject = new JsonObject();
    benchmarkObject.addProperty("benchmark_name", result.getName());
    benchmarkObject.add("results", resultsArray);
    return benchmarkObject;
  }
}
