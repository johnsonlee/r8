// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ListUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public class ConnectedComponentVerticalClassMerger {

  private final AppView<AppInfoWithLiveness> appView;
  private final Collection<VerticalMergeGroup> groups;

  // The resulting graph lens that should be used after class merging.
  private final VerticalClassMergerGraphLens.Builder lensBuilder;

  // All the bridge methods that have been synthesized during vertical class merging.
  private final List<IncompleteVerticalClassMergerBridgeCode> synthesizedBridges =
      new ArrayList<>();

  private final VerticallyMergedClasses.Builder verticallyMergedClassesBuilder =
      VerticallyMergedClasses.builder();

  ConnectedComponentVerticalClassMerger(
      AppView<AppInfoWithLiveness> appView, Collection<VerticalMergeGroup> groups) {
    this.appView = appView;
    this.groups = groups;
    this.lensBuilder = new VerticalClassMergerGraphLens.Builder();
  }

  public boolean isEmpty() {
    return groups.isEmpty();
  }

  public VerticalClassMergerResult.Builder run() {
    List<VerticalMergeGroup> groupsSorted =
        ListUtils.sort(groups, Comparator.comparing(group -> group.getSource().getType()));
    List<ClassMerger> classMergers =
        ListUtils.map(
            groupsSorted,
            group ->
                new ClassMerger(
                    appView,
                    lensBuilder,
                    synthesizedBridges,
                    verticallyMergedClassesBuilder,
                    group));
    classMergers.forEach(ClassMerger::setup);
    classMergers.forEach(ClassMerger::merge);
    return VerticalClassMergerResult.builder(
        lensBuilder, synthesizedBridges, verticallyMergedClassesBuilder);
  }
}
