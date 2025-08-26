// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.kotlin.KotlinSourceDebugExtensionParser;
import com.android.tools.r8.kotlin.KotlinSourceDebugExtensionParser.KotlinSourceDebugExtensionParserResult;
import com.android.tools.r8.utils.timing.Timing;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class KotlinSourceDebugExtensionCollection {

  private final Map<DexProgramClass, KotlinSourceDebugExtensionParserResult>
      classDescriptorToKotlinSourceDebugExtension;

  private KotlinSourceDebugExtensionCollection(
      Map<DexProgramClass, KotlinSourceDebugExtensionParserResult>
          classDescriptorToKotlinSourceDebugExtension) {
    this.classDescriptorToKotlinSourceDebugExtension = classDescriptorToKotlinSourceDebugExtension;
  }

  /** Creates a map from each program class to its (parsed) Kotlin source debug extension. */
  public static KotlinSourceDebugExtensionCollection create(AppView<?> appView, Timing timing) {
    try (Timing t0 = timing.begin("Create KotlinSourceDebugExtensionCollection")) {
      Map<DexProgramClass, KotlinSourceDebugExtensionParserResult>
          classDescriptorToKotlinSourceDebugExtension = new HashMap<>();
      if (appView.hasSourceDebugExtensions()) {
        for (DexProgramClass clazz : appView.appInfo().classes()) {
          KotlinSourceDebugExtensionParserResult sourceDebugExtension =
              KotlinSourceDebugExtensionParser.parse(appView.getSourceDebugExtensionForType(clazz));
          if (sourceDebugExtension != null) {
            classDescriptorToKotlinSourceDebugExtension.put(clazz, sourceDebugExtension);
          }
        }
      }
      return new KotlinSourceDebugExtensionCollection(classDescriptorToKotlinSourceDebugExtension);
    }
  }

  public KotlinSourceDebugExtensionParserResult get(DexProgramClass clazz) {
    return classDescriptorToKotlinSourceDebugExtension.get(clazz);
  }

  public Collection<KotlinSourceDebugExtensionParserResult> values() {
    return classDescriptorToKotlinSourceDebugExtension.values();
  }
}
