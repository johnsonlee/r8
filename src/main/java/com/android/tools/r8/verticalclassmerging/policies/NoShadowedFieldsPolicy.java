// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.TraversalContinuation;
import com.android.tools.r8.verticalclassmerging.VerticalMergeGroup;

public class NoShadowedFieldsPolicy extends VerticalClassMergerPolicy {

  private final AppView<AppInfoWithLiveness> appView;

  public NoShadowedFieldsPolicy(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  @Override
  public boolean canMerge(VerticalMergeGroup group) {
    TraversalContinuation<?, ?> traversalContinuation =
        group
            .getTarget()
            .traverseProgramFields(
                field ->
                    TraversalContinuation.breakIf(
                        appView
                            .appInfo()
                            .resolveFieldOn(group.getSource(), field.getReference())
                            .isSingleFieldResolutionResult()));
    return traversalContinuation.isContinue();
  }

  @Override
  public String getName() {
    return "NoShadowedFieldsPolicy";
  }
}
