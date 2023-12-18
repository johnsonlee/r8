// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging.policies;

import com.android.tools.r8.verticalclassmerging.VerticalMergeGroup;

public class NoInnerClassAttributesPolicy extends VerticalClassMergerPolicy {

  @Override
  public boolean canMerge(VerticalMergeGroup group) {
    return group.getSource().getInnerClasses().isEmpty()
        && group.getTarget().getInnerClasses().isEmpty();
  }

  @Override
  public String getName() {
    return "NoInnerClassAttributesPolicy";
  }
}
