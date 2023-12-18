// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DefaultUseRegistryWithResult;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.MethodLookupResult;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public class MergeMayLeadToNoSuchMethodErrorUseRegistry
    extends DefaultUseRegistryWithResult<Boolean, ProgramMethod> {

  private final AppView<AppInfoWithLiveness> appViewWithLiveness;
  private final GraphLens graphLens;
  private final GraphLens codeLens;
  private final DexProgramClass source;

  public MergeMayLeadToNoSuchMethodErrorUseRegistry(
      AppView<AppInfoWithLiveness> appView, ProgramMethod context, DexProgramClass source) {
    super(appView, context, Boolean.FALSE);
    assert context.getHolder().getSuperType().isIdenticalTo(source.getType());
    this.appViewWithLiveness = appView;
    this.graphLens = appView.graphLens();
    this.codeLens = context.getDefinition().getCode().getCodeLens(appView);
    this.source = source;
  }

  public boolean mayLeadToNoSuchMethodError() {
    return getResult();
  }

  /**
   * Sets the result of this registry to true if (1) it finds an invoke-super instruction that
   * targets a (default) interface method and (2) the invoke-super instruction does not have a
   * target when the context of the super class is used.
   */
  @Override
  public void registerInvokeSuper(DexMethod method) {
    MethodLookupResult lookupResult = graphLens.lookupInvokeSuper(method, getContext(), codeLens);
    DexMethod rewrittenMethod = lookupResult.getReference();
    MethodResolutionResult currentResolutionResult =
        appViewWithLiveness.appInfo().unsafeResolveMethodDueToDexFormat(rewrittenMethod);
    DexClassAndMethod currentSuperTarget =
        currentResolutionResult.lookupInvokeSuperTarget(getContext(), appViewWithLiveness);
    if (currentSuperTarget == null || !currentSuperTarget.getHolder().isInterface()) {
      return;
    }

    MethodResolutionResult parentResolutionResult =
        appViewWithLiveness.appInfo().resolveMethodOnClass(source, method);
    DexClassAndMethod parentSuperTarget =
        parentResolutionResult.lookupInvokeSuperTarget(source, appViewWithLiveness);
    if (parentSuperTarget == null) {
      setResult(true);
    }
  }
}
