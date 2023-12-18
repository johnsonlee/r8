// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ListUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class ConnectedComponentVerticalClassMerger {

  private final AppView<AppInfoWithLiveness> appView;
  private final Collection<VerticalMergeGroup> classesToMerge;

  // The resulting graph lens that should be used after class merging.
  private final VerticalClassMergerGraphLens.Builder lensBuilder;

  // All the bridge methods that have been synthesized during vertical class merging.
  private final List<SynthesizedBridgeCode> synthesizedBridges = new ArrayList<>();

  private final VerticallyMergedClasses.Builder verticallyMergedClassesBuilder =
      VerticallyMergedClasses.builder();

  ConnectedComponentVerticalClassMerger(
      AppView<AppInfoWithLiveness> appView, Collection<VerticalMergeGroup> classesToMerge) {
    this.appView = appView;
    this.classesToMerge = classesToMerge;
    this.lensBuilder = new VerticalClassMergerGraphLens.Builder();
  }

  public boolean isEmpty() {
    return classesToMerge.isEmpty();
  }

  public VerticalClassMergerResult.Builder run() throws ExecutionException {
    List<VerticalMergeGroup> classesToMergeSorted =
        ListUtils.sort(classesToMerge, Comparator.comparing(group -> group.getSource().getType()));
    for (VerticalMergeGroup group : classesToMergeSorted) {
      mergeClassIfPossible(group);
    }
    return VerticalClassMergerResult.builder(
        lensBuilder, synthesizedBridges, verticallyMergedClassesBuilder);
  }

  private void mergeClassIfPossible(VerticalMergeGroup group) throws ExecutionException {
    DexProgramClass sourceClass = group.getSource();
    DexProgramClass targetClass = group.getTarget();
    if (verticallyMergedClassesBuilder.isMergeSource(targetClass)
        || verticallyMergedClassesBuilder.isMergeTarget(sourceClass)) {
      return;
    }
    ClassMerger merger =
        new ClassMerger(
            appView, lensBuilder, verticallyMergedClassesBuilder, sourceClass, targetClass);
    if (merger.merge()) {
      verticallyMergedClassesBuilder.add(sourceClass, targetClass);
      // Commit the changes to the graph lens.
      lensBuilder.merge(merger.getRenamings());
      synthesizedBridges.addAll(merger.getSynthesizedBridges());
    }
  }
}
