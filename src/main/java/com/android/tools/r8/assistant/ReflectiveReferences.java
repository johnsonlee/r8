// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;

public class ReflectiveReferences {

  public final DexType reflectiveOracleType;
  public final DexType reflectiveOperationReceiverType;
  public final DexMethod getReceiverMethod;

  public ReflectiveReferences(DexItemFactory factory) {
    this.reflectiveOracleType = factory.createType(getDescriptor("ReflectiveOracle"));
    this.reflectiveOperationReceiverType =
        factory.createType(getDescriptor("ReflectiveOperationReceiver"));
    this.getReceiverMethod =
        factory.createMethod(
            reflectiveOracleType,
            factory.createProto(reflectiveOperationReceiverType),
            "getReceiver");
  }

  private static String getDescriptor(String className) {
    return "Lcom/android/tools/r8/assistant/runtime/" + className + ";";
  }
}
