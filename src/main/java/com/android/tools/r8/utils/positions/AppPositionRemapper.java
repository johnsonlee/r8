// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.positions;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.CfLineToMethodMapper;
import com.android.tools.r8.utils.KotlinSourceDebugExtensionCollection;
import com.android.tools.r8.utils.positions.ClassPositionRemapper.IdentityPositionRemapper;
import com.android.tools.r8.utils.positions.ClassPositionRemapper.KotlinInlineFunctionAppPositionRemapper;
import com.android.tools.r8.utils.positions.ClassPositionRemapper.OptimizingPositionRemapper;
import com.android.tools.r8.utils.timing.Timing;

public interface AppPositionRemapper {

  ClassPositionRemapper createClassPositionRemapper(DexProgramClass clazz);

  static AppPositionRemapper create(AppView<?> appView, AndroidApp inputApp, Timing timing) {
    boolean identityMapping = appView.options().lineNumberOptimization.isOff();
    AppPositionRemapper positionRemapper =
        identityMapping
            ? new IdentityPositionRemapper()
            : new OptimizingPositionRemapper(appView.options());

    // Kotlin inline functions and arguments have their inlining information stored in the
    // source debug extension annotation. Instantiate the kotlin remapper on top of the original
    // remapper to allow for remapping original positions to kotlin inline positions.
    KotlinSourceDebugExtensionCollection kotlinSourceDebugExtensions =
        KotlinSourceDebugExtensionCollection.create(appView, timing);
    // For finding methods in kotlin files based on SourceDebugExtensions, we use a line method map.
    // We create it here to ensure it is only reading class files once.
    CfLineToMethodMapper cfLineToMethodMapper =
        CfLineToMethodMapper.create(inputApp, kotlinSourceDebugExtensions, timing);
    return new KotlinInlineFunctionAppPositionRemapper(
        appView, positionRemapper, cfLineToMethodMapper, kotlinSourceDebugExtensions);
  }
}
