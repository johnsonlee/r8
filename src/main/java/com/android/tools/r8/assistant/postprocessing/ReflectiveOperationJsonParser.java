// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.assistant.postprocessing;

import com.android.tools.r8.assistant.postprocessing.model.ReflectiveEvent;
import com.android.tools.r8.assistant.runtime.ReflectiveEventType;
import com.android.tools.r8.graph.DexItemFactory;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ReflectiveOperationJsonParser {

  private final DexItemFactory factory;

  public ReflectiveOperationJsonParser(DexItemFactory factory) {
    this.factory = factory;
  }

  public List<ReflectiveEvent> parse(Path file) throws IOException {
    List<ReflectiveEvent> result = new ArrayList<>();
    String contents = Files.readString(file) + "{}]";
    JsonArray events = new JsonParser().parse(contents).getAsJsonArray();
    for (JsonElement eventElement : events) {
      JsonObject event = eventElement.getAsJsonObject();
      if (event.isEmpty()) {
        break;
      }
      ReflectiveEventType eventType = ReflectiveEventType.valueOf(event.get("event").getAsString());
      JsonElement stackElement = event.get("stack");
      String[] stack = stackElement != null ? toStringArray(stackElement) : null;
      JsonElement argsElement = event.get("args");
      String[] args = argsElement != null ? toStringArray(argsElement) : null;
      result.add(ReflectiveEvent.instantiate(eventType, stack, args, factory));
    }
    return result;
  }

  private String[] toStringArray(JsonElement argsElement) {
    JsonArray jsonArray = argsElement.getAsJsonArray();
    String[] strings = new String[jsonArray.size()];
    for (int i = 0; i < strings.length; i++) {
      strings[i] = jsonArray.get(i).getAsString();
    }
    return strings;
  }
}
