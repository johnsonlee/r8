// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging.policies;

import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.utils.ObjectUtils;
import com.android.tools.r8.verticalclassmerging.VerticalMergeGroup;

public class SameNestPolicy extends VerticalClassMergerPolicy {

  @Override
  public boolean canMerge(VerticalMergeGroup group) {
    // We abort class merging when merging across nests or from a nest to non-nest.
    // Without nest this checks null == null.
    DexProgramClass sourceClass = group.getSource();
    DexProgramClass targetClass = group.getTarget();
    return ObjectUtils.identical(targetClass.getNestHost(), sourceClass.getNestHost());
  }

  @Override
  public String getName() {
    return "SameNestPolicy";
  }
}
