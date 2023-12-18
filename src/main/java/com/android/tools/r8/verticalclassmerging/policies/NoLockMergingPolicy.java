// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.verticalclassmerging.VerticalMergeGroup;

public class NoLockMergingPolicy extends VerticalClassMergerPolicy {

  private final AppView<AppInfoWithLiveness> appView;

  public NoLockMergingPolicy(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  @Override
  public boolean canMerge(VerticalMergeGroup group) {
    DexProgramClass sourceClass = group.getSource();
    DexProgramClass targetClass = group.getTarget();
    boolean sourceCanBeSynchronizedOn =
        appView.appInfo().isLockCandidate(sourceClass)
            || sourceClass.hasStaticSynchronizedMethods();
    boolean targetCanBeSynchronizedOn =
        appView.appInfo().isLockCandidate(targetClass)
            || targetClass.hasStaticSynchronizedMethods();
    return !sourceCanBeSynchronizedOn || !targetCanBeSynchronizedOn;
  }

  @Override
  public String getName() {
    return "NoLockMergingPolicy";
  }
}
