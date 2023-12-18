// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging.policies;

import static com.android.tools.r8.utils.AndroidApiLevelUtils.getApiReferenceLevelForMerging;

import com.android.tools.r8.androidapi.AndroidApiLevelCompute;
import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.verticalclassmerging.VerticalMergeGroup;

public class SameApiReferenceLevelPolicy extends VerticalClassMergerPolicy {

  private final AppView<AppInfoWithLiveness> appView;

  public SameApiReferenceLevelPolicy(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  @Override
  public boolean canMerge(VerticalMergeGroup group) {
    assert appView.options().apiModelingOptions().isApiCallerIdentificationEnabled();
    DexProgramClass sourceClass = group.getSource();
    DexProgramClass targetClass = group.getTarget();
    // Only merge if api reference level of source class is equal to target class. The check is
    // somewhat expensive.
    AndroidApiLevelCompute apiLevelCompute = appView.apiLevelCompute();
    ComputedApiLevel sourceApiLevel = getApiReferenceLevelForMerging(apiLevelCompute, sourceClass);
    ComputedApiLevel targetApiLevel = getApiReferenceLevelForMerging(apiLevelCompute, targetClass);
    return sourceApiLevel.equals(targetApiLevel);
  }

  @Override
  public String getName() {
    return "SameApiReferenceLevelPolicy";
  }

  @Override
  public boolean shouldSkipPolicy() {
    return !appView.options().apiModelingOptions().isApiCallerIdentificationEnabled();
  }
}
