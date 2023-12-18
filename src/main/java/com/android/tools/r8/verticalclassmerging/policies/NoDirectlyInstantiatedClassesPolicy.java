// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ObjectAllocationInfoCollection;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.verticalclassmerging.VerticalMergeGroup;

public class NoDirectlyInstantiatedClassesPolicy extends VerticalClassMergerPolicy {

  private final ObjectAllocationInfoCollection allocationInfo;

  public NoDirectlyInstantiatedClassesPolicy(AppView<AppInfoWithLiveness> appView) {
    allocationInfo = appView.appInfo().getObjectAllocationInfoCollection();
  }

  @Override
  public boolean canMerge(VerticalMergeGroup group) {
    DexProgramClass sourceClass = group.getSource();
    return !allocationInfo.isInstantiatedDirectly(sourceClass);
  }

  @Override
  public String getName() {
    return "NoDirectlyInstantiatedClassesPolicy";
  }
}
