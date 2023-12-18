// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.TraversalContinuation;
import com.android.tools.r8.verticalclassmerging.MergeMayLeadToNoSuchMethodErrorUseRegistry;
import com.android.tools.r8.verticalclassmerging.VerticalMergeGroup;

public class NoInvokeSuperNoSuchMethodErrorsPolicy extends VerticalClassMergerPolicy {

  private final AppView<AppInfoWithLiveness> appView;
  private final InternalOptions options;

  public NoInvokeSuperNoSuchMethodErrorsPolicy(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
    this.options = appView.options();
  }

  @Override
  public boolean canMerge(VerticalMergeGroup group) {
    return !mergeMayLeadToNoSuchMethodError(group.getSource(), group.getTarget());
  }

  private boolean mergeMayLeadToNoSuchMethodError(DexProgramClass source, DexProgramClass target) {
    // This only returns true when an invoke-super instruction is found that targets a default
    // interface method.
    if (!options.canUseDefaultAndStaticInterfaceMethods()) {
      return false;
    }
    // This problem may only arise when merging (non-interface) classes into classes.
    if (source.isInterface() || target.isInterface()) {
      return false;
    }
    return target
        .traverseProgramMethods(
            method -> {
              MergeMayLeadToNoSuchMethodErrorUseRegistry registry =
                  new MergeMayLeadToNoSuchMethodErrorUseRegistry(appView, method, source);
              method.registerCodeReferencesWithResult(registry);
              return TraversalContinuation.breakIf(registry.mayLeadToNoSuchMethodError());
            },
            DexEncodedMethod::hasCode)
        .shouldBreak();
  }

  @Override
  public String getName() {
    return "NoInvokeSuperNoSuchMethodErrorsPolicy";
  }
}
