// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.horizontalclassmerging.HorizontalMergeGroup;
import com.android.tools.r8.horizontalclassmerging.MultiClassPolicy;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.collections.EmptyBidirectionalOneToOneMap;
import java.util.Collection;

/**
 * Identifies when instance initializer merging is required and bails out. This is needed to ensure
 * that we don't need to append extra null arguments at constructor call sites, such that the result
 * of the final round of class merging can be described as a renaming only.
 *
 * <p>This policy requires that all instance initializers with the same signature (relaxed, by
 * converting references types to java.lang.Object) have the same behavior.
 */
public class FinalizeMergeGroup extends MultiClassPolicy {

  private final AppView<?> appView;

  public FinalizeMergeGroup(AppView<?> appView) {
    this.appView = appView;
  }

  @Override
  public Collection<HorizontalMergeGroup> apply(HorizontalMergeGroup group) {
    if (appView.enableWholeProgramOptimizations()) {
      group.selectTarget(appView);
      group.selectInstanceFieldMap(appView.withClassHierarchy());
    } else {
      assert !group.hasTarget();
      assert !group.hasInstanceFieldMap();
      group.selectTarget(appView);
      group.setInstanceFieldMap(new EmptyBidirectionalOneToOneMap<>());
    }
    return ListUtils.newLinkedList(group);
  }

  @Override
  public String getName() {
    return "FinalizeMergeGroup";
  }

  @Override
  public boolean isIdentityForInterfaceGroups() {
    return true;
  }
}
