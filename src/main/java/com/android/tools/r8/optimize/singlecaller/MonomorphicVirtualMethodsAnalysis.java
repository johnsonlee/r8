// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.singlecaller;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.VirtualRootMethodsAnalysisBase;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class MonomorphicVirtualMethodsAnalysis extends VirtualRootMethodsAnalysisBase {

  public MonomorphicVirtualMethodsAnalysis(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo) {
    super(appView, immediateSubtypingInfo);
  }

  public static ProgramMethodSet computeMonomorphicVirtualRootMethods(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo,
      List<Set<DexProgramClass>> stronglyConnectedComponents,
      ExecutorService executorService)
      throws ExecutionException {
    ProgramMethodSet monomorphicVirtualMethods = ProgramMethodSet.createConcurrent();
    ThreadUtils.processItems(
        stronglyConnectedComponents,
        stronglyConnectedComponent -> {
          ProgramMethodSet monomorphicVirtualMethodsInComponent =
              computeMonomorphicVirtualRootMethodsInComponent(
                  appView, immediateSubtypingInfo, stronglyConnectedComponent);
          monomorphicVirtualMethods.addAll(monomorphicVirtualMethodsInComponent);
        },
        appView.options().getThreadingModule(),
        executorService);
    return monomorphicVirtualMethods;
  }

  private static ProgramMethodSet computeMonomorphicVirtualRootMethodsInComponent(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo,
      Set<DexProgramClass> stronglyConnectedComponent) {
    MonomorphicVirtualMethodsAnalysis analysis =
        new MonomorphicVirtualMethodsAnalysis(appView, immediateSubtypingInfo);
    analysis.run(stronglyConnectedComponent);
    return analysis.monomorphicVirtualRootMethods;
  }
}
