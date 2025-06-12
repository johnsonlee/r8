// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.info;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.utils.ThreadUtils;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * Clears any optimization info on fields and methods that need lens code rewriting. This avoids the
 * need to lens code rewrite such optimization info in repackaging and other optimizations where the
 * optimization info is mostly unused, since no more optimizations passes will be run.
 */
public class OptimizationInfoRemover {

  public static void run(
      AppView<? extends AppInfoWithClassHierarchy> appView, ExecutorService executorService)
      throws ExecutionException {
    ThreadUtils.processItems(
        appView.appInfo().classes(),
        OptimizationInfoRemover::processClass,
        appView.options().getThreadingModule(),
        executorService);
  }

  private static void processClass(DexProgramClass clazz) {
    for (DexEncodedField field : clazz.fields()) {
      processField(field);
    }
    for (DexEncodedMethod method : clazz.methods()) {
      processMethod(method);
    }
  }

  private static void processField(DexEncodedField field) {
    MutableFieldOptimizationInfo optimizationInfo =
        field.getOptimizationInfo().asMutableFieldOptimizationInfo();
    if (optimizationInfo == null) {
      return;
    }
    optimizationInfo.unsetAbstractValue();
    optimizationInfo.unsetDynamicType();
  }

  public static void processMethod(DexEncodedMethod method) {
    MutableMethodOptimizationInfo optimizationInfo =
        method.getOptimizationInfo().asMutableMethodOptimizationInfo();
    if (optimizationInfo == null) {
      return;
    }
    optimizationInfo.unsetAbstractReturnValue();
    optimizationInfo.unsetArgumentInfos();
    optimizationInfo.unsetDynamicType();
    optimizationInfo.unsetInitializedClassesOnNormalExit();
    optimizationInfo.unsetInstanceInitializerInfoCollection();
    optimizationInfo.unsetNopInliningConstraint();
    optimizationInfo.unsetSimpleInliningConstraint();
    if (optimizationInfo.isEffectivelyDefault()) {
      method.unsetOptimizationInfo();
    }
  }
}
