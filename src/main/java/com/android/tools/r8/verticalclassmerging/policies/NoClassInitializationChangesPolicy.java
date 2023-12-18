// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.verticalclassmerging.VerticalMergeGroup;

public class NoClassInitializationChangesPolicy extends VerticalClassMergerPolicy {

  private final AppView<AppInfoWithLiveness> appView;

  public NoClassInitializationChangesPolicy(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  @Override
  public boolean canMerge(VerticalMergeGroup group) {
    // For interface types, this is more complicated, see:
    // https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-5.html#jvms-5.5
    // We basically can't move the clinit, since it is not called when implementing classes have
    // their clinit called - except when the interface has a default method.
    DexProgramClass sourceClass = group.getSource();
    DexProgramClass targetClass = group.getTarget();
    return (!sourceClass.hasClassInitializer() || !targetClass.hasClassInitializer())
        && !targetClass.classInitializationMayHaveSideEffects(
            appView, type -> type.isIdenticalTo(sourceClass.getType()))
        && (!sourceClass.isInterface()
            || !sourceClass.classInitializationMayHaveSideEffects(appView));
  }

  @Override
  public String getName() {
    return "NoClassInitializationChangesPolicy";
  }
}
