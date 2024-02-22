// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.TraversalContinuation;
import com.android.tools.r8.verticalclassmerging.InvokeSpecialToDefaultLibraryMethodUseRegistry;
import com.android.tools.r8.verticalclassmerging.VerticalMergeGroup;

public class NoInterfacesWithInvokeSpecialToDefaultMethodIntoClassPolicy
    extends VerticalClassMergerPolicy {

  private final AppView<AppInfoWithLiveness> appView;

  public NoInterfacesWithInvokeSpecialToDefaultMethodIntoClassPolicy(
      AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  @Override
  public boolean canMerge(VerticalMergeGroup group) {
    DexProgramClass sourceClass = group.getSource();
    DexProgramClass targetClass = group.getTarget();
    // If there is an invoke-special to a default interface method and we are not merging into an
    // interface, then abort, since invoke-special to a virtual class method requires desugaring.
    if (!sourceClass.isInterface() || targetClass.isInterface()) {
      return true;
    }
    TraversalContinuation<?, ?> result =
        sourceClass.traverseProgramMethods(
            method -> {
              boolean foundInvokeSpecialToDefaultLibraryMethod =
                  method.registerCodeReferencesWithResult(
                      new InvokeSpecialToDefaultLibraryMethodUseRegistry(appView, method));
              return TraversalContinuation.breakIf(foundInvokeSpecialToDefaultLibraryMethod);
            });
    return result.shouldContinue();
  }

  @Override
  public String getName() {
    return "NoInterfacesWithInvokeSpecialToDefaultMethodIntoClassPolicy";
  }
}
