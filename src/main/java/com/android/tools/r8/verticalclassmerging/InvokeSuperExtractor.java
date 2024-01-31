// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DefaultUseRegistry;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.MethodLookupResult;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.collections.DexMethodSignatureSet;

public class InvokeSuperExtractor extends DefaultUseRegistry<ProgramMethod> {

  private final AppView<AppInfoWithLiveness> appViewWithLiveness;
  private final GraphLens graphLens;
  private final GraphLens codeLens;
  private final DexMethodSignatureSet methodsOfInterest;
  private final DexMethodSignatureSet result;
  private final DexProgramClass source;

  public InvokeSuperExtractor(
      AppView<AppInfoWithLiveness> appView,
      ProgramMethod context,
      DexMethodSignatureSet methodsOfInterest,
      DexMethodSignatureSet result,
      DexProgramClass source) {
    super(appView, context);
    this.appViewWithLiveness = appView;
    this.graphLens = appView.graphLens();
    this.codeLens = context.getDefinition().getCode().getCodeLens(appView);
    this.methodsOfInterest = methodsOfInterest;
    this.result = result;
    this.source = source;
  }

  // TODO(b/323022702): This should not need to be overridden, but we sometimes (correctly) map an
  //  invoke-special to invoke-direct, but then later decides to map the same invoke-special to
  //  invoke-super.
  @Override
  public void registerInvokeSpecial(DexMethod method) {
    handleInvokeSpecial(method);
  }

  @Override
  public void registerInvokeSuper(DexMethod method) {
    handleInvokeSpecial(method);
  }

  private void handleInvokeSpecial(DexMethod method) {
    MethodLookupResult lookupResult = graphLens.lookupInvokeSuper(method, getContext(), codeLens);
    DexMethod rewrittenMethod = lookupResult.getReference();
    if (!methodsOfInterest.contains(rewrittenMethod) || result.contains(rewrittenMethod)) {
      return;
    }
    MethodResolutionResult currentResolutionResult =
        appViewWithLiveness.appInfo().unsafeResolveMethodDueToDexFormat(rewrittenMethod);
    DexClassAndMethod superTarget =
        currentResolutionResult.lookupInvokeSuperTarget(getContext(), appViewWithLiveness);
    if (superTarget != null && superTarget.getHolder() == source) {
      result.add(superTarget);
    }
  }
}
