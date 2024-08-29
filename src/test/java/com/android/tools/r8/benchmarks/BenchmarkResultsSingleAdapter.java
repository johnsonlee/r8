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
import java.util.Collection;
import java.util.function.IntToLongFunction;

public class BenchmarkResultsSingleAdapter implements JsonSerializer<BenchmarkResultsSingle> {

  @Override
  public JsonElement serialize(
      BenchmarkResultsSingle result, Type type, JsonSerializationContext jsonSerializationContext) {
    JsonArray resultsArray = new JsonArray();
    for (int iteration = 0; iteration < result.getCodeSizeResults().size(); iteration++) {
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
            CaseFormat.UPPER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, sectionName);
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
          result.getDex2OatSizeResult(),
          i -> result.getDex2OatSizeResult().getLong(i));
      addPropertyIfValueDifferentFromRepresentative(
          resultObject,
          "runtime",
          iteration,
          result.getRuntimeResults(),
          i -> result.getRuntimeResults().getLong(i));
          resultsArray.add(resultObject);
    }

    JsonObject benchmarkObject = new JsonObject();
    benchmarkObject.addProperty("benchmark_name", result.getName());
    benchmarkObject.add("results", resultsArray);
    return benchmarkObject;
  }

  private void addPropertyIfValueDifferentFromRepresentative(
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
