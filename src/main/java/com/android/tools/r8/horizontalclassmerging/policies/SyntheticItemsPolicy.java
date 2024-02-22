// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.MultiClassSameReferencePolicy;
import com.android.tools.r8.horizontalclassmerging.policies.SyntheticItemsPolicy.ClassKind;
import com.android.tools.r8.synthesis.SyntheticItems;

public class SyntheticItemsPolicy extends MultiClassSameReferencePolicy<ClassKind> {

  enum ClassKind {
    SYNTHETIC,
    NOT_SYNTHETIC
  }

  private final SyntheticItems syntheticItems;

  public SyntheticItemsPolicy(AppView<?> appView) {
    this.syntheticItems = appView.getSyntheticItems();
  }

  @Override
  public ClassKind getMergeKey(DexProgramClass clazz) {
    // Allow merging non-synthetics with non-synthetics, and synthetics with synthetics.
    if (syntheticItems.isSyntheticClass(clazz)) {
      return ClassKind.SYNTHETIC;
    }
    return ClassKind.NOT_SYNTHETIC;
  }

  @Override
  public String getName() {
    return "SyntheticItemsPolicy";
  }
}
