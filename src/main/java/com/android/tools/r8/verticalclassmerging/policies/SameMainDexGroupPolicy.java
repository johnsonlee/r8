// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.MainDexInfo;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.verticalclassmerging.VerticalMergeGroup;

public class SameMainDexGroupPolicy extends VerticalClassMergerPolicy {

  private final MainDexInfo mainDexInfo;
  private final SyntheticItems syntheticItems;

  public SameMainDexGroupPolicy(AppView<AppInfoWithLiveness> appView) {
    this.mainDexInfo = appView.appInfo().getMainDexInfo();
    this.syntheticItems = appView.getSyntheticItems();
  }

  @Override
  public boolean canMerge(VerticalMergeGroup group) {
    assert !mainDexInfo.isNone();
    DexProgramClass sourceClass = group.getSource();
    DexProgramClass targetClass = group.getTarget();
    return mainDexInfo.canMerge(sourceClass, targetClass, syntheticItems);
  }

  @Override
  public String getName() {
    return "SameMainDexGroupPolicy";
  }

  @Override
  public boolean shouldSkipPolicy() {
    return mainDexInfo.isNone();
  }
}
