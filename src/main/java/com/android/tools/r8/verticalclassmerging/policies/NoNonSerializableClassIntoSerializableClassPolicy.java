// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.verticalclassmerging.VerticalMergeGroup;

public class NoNonSerializableClassIntoSerializableClassPolicy extends VerticalClassMergerPolicy {

  private final AppView<AppInfoWithLiveness> appView;

  public NoNonSerializableClassIntoSerializableClassPolicy(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  @Override
  public boolean canMerge(VerticalMergeGroup group) {
    DexProgramClass sourceClass = group.getSource();
    DexProgramClass targetClass = group.getTarget();
    // https://docs.oracle.com/javase/8/docs/platform/serialization/spec/serial-arch.html
    //   1.10 The Serializable Interface
    //   ...
    //   A Serializable class must do the following:
    //   ...
    //     * Have access to the no-arg constructor of its first non-serializable superclass
    return sourceClass.isInterface()
        || !targetClass.isSerializable(appView)
        || sourceClass.isSerializable(appView);
  }

  @Override
  public String getName() {
    return "NoNonSerializableClassIntoSerializableClassPolicy";
  }
}
