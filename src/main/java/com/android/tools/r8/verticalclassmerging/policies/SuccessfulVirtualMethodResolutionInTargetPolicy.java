// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.verticalclassmerging.VerticalMergeGroup;

public class SuccessfulVirtualMethodResolutionInTargetPolicy extends VerticalClassMergerPolicy {

  private final AppView<AppInfoWithLiveness> appView;

  public SuccessfulVirtualMethodResolutionInTargetPolicy(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  @Override
  public boolean canMerge(VerticalMergeGroup group) {
    for (ProgramMethod method : group.getSource().virtualProgramMethods()) {
      MethodResolutionResult resolutionResult =
          appView.appInfo().resolveMethodOn(group.getTarget(), method.getReference());
      if (!resolutionResult.isSingleResolution()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public String getName() {
    return "SuccessfulVirtualMethodResolutionInTargetPolicy";
  }
}
