// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.profile.startup.optimization.StartupBoundaryOptimizationUtils;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.verticalclassmerging.VerticalMergeGroup;

public class SameStartupPartitionPolicy extends VerticalClassMergerPolicy {

  private final AppView<AppInfoWithLiveness> appView;

  public SameStartupPartitionPolicy(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  @Override
  public boolean canMerge(VerticalMergeGroup group) {
    DexProgramClass sourceClass = group.getSource();
    DexProgramClass targetClass = group.getTarget();
    return StartupBoundaryOptimizationUtils.isSafeForVerticalClassMerging(
        sourceClass, targetClass, appView);
  }

  @Override
  public String getName() {
    return "SameStartupPartitionPolicy";
  }
}
