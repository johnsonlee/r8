// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.verticalclassmerging.VerticalMergeGroup;
import java.util.Set;

public class NoKeptClassesPolicy extends VerticalClassMergerPolicy {

  private final AppView<AppInfoWithLiveness> appView;
  private final Set<DexProgramClass> keptClasses;
  private final InternalOptions options;

  public NoKeptClassesPolicy(
      AppView<AppInfoWithLiveness> appView, Set<DexProgramClass> keptClasses) {
    this.appView = appView;
    this.keptClasses = keptClasses;
    this.options = appView.options();
  }

  @Override
  public boolean canMerge(VerticalMergeGroup group) {
    DexProgramClass sourceClass = group.getSource();
    return appView.getKeepInfo(sourceClass).isVerticalClassMergingAllowed(options)
        && !keptClasses.contains(sourceClass);
  }

  @Override
  public String getName() {
    return "NoKeptClassesPolicy";
  }
}
