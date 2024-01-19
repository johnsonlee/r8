// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging.policies;

import com.android.tools.r8.classmerging.ClassMergerMode;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.verticalclassmerging.VerticalMergeGroup;
import com.google.common.collect.Iterables;

public class NoFinalSourceInstanceFieldsWithConstructorInliningPolicy
    extends VerticalClassMergerPolicy {

  private final ClassMergerMode mode;
  private final InternalOptions options;

  public NoFinalSourceInstanceFieldsWithConstructorInliningPolicy(
      AppView<AppInfoWithLiveness> appView, ClassMergerMode mode) {
    this.mode = mode;
    this.options = appView.options();
  }

  @Override
  public boolean canMerge(VerticalMergeGroup group) {
    return group.getSource().isInterface()
        || !options.canInitNewInstanceUsingSuperclassConstructor()
        || Iterables.isEmpty(group.getSource().instanceFields(DexEncodedField::isFinal));
  }

  @Override
  public boolean shouldSkipPolicy() {
    return mode.isInitial();
  }

  @Override
  public String getName() {
    return "NoFinalSourceInstanceFieldsWithConstructorInliningPolicy";
  }
}
