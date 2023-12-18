// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging.policies;

import com.android.tools.r8.classmerging.Policy;
import com.android.tools.r8.verticalclassmerging.VerticalMergeGroup;

public abstract class VerticalClassMergerPolicy extends Policy {

  public abstract boolean canMerge(VerticalMergeGroup group);

  @Override
  public boolean isVerticalClassMergerPolicy() {
    return true;
  }

  @Override
  public VerticalClassMergerPolicy asVerticalClassMergerPolicy() {
    return this;
  }
}
