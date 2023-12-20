// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging.policies;

import com.android.tools.r8.verticalclassmerging.VerticalMergeGroup;
import java.util.Collection;

public abstract class VerticalClassMergerPolicy
    extends VerticalClassMergerPolicyWithPreprocessing<Void> {

  public abstract boolean canMerge(VerticalMergeGroup group);

  @Override
  public final boolean canMerge(VerticalMergeGroup group, Void data) {
    return canMerge(group);
  }

  @Override
  public final Void preprocess(Collection<VerticalMergeGroup> groups) {
    return null;
  }
}
