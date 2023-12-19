// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging.policies;

import com.android.tools.r8.classmerging.Policy;
import com.android.tools.r8.verticalclassmerging.VerticalMergeGroup;
import java.util.Collection;

public abstract class VerticalClassMergerPolicyWithPreprocessing<T> extends Policy {

  public abstract boolean canMerge(VerticalMergeGroup group, T data);

  public abstract T preprocess(Collection<VerticalMergeGroup> groups);

  @Override
  public boolean isVerticalClassMergerPolicyWithPreprocessing() {
    return true;
  }

  @Override
  public VerticalClassMergerPolicyWithPreprocessing<T>
      asVerticalClassMergerPolicyWithPreprocessing() {
    return this;
  }
}
