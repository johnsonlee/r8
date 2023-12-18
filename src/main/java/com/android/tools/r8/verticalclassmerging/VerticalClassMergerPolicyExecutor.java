// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.horizontalclassmerging.Policy;
import com.android.tools.r8.horizontalclassmerging.PolicyExecutor;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.Timing;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

// TODO(b/315252934): Parallelize policy execution over connected program components.
public class VerticalClassMergerPolicyExecutor extends PolicyExecutor<VerticalMergeGroup> {

  private final AppView<AppInfoWithLiveness> appView;
  private final ImmediateProgramSubtypingInfo immediateSubtypingInfo;
  private final Set<DexProgramClass> pinnedClasses;

  VerticalClassMergerPolicyExecutor(
      AppView<AppInfoWithLiveness> appView,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo,
      Set<DexProgramClass> pinnedClasses) {
    this.appView = appView;
    this.immediateSubtypingInfo = immediateSubtypingInfo;
    this.pinnedClasses = pinnedClasses;
  }

  ConnectedComponentVerticalClassMerger run(
      Set<DexProgramClass> connectedComponent, ExecutorService executorService, Timing timing)
      throws ExecutionException {
    Collection<VerticalMergeGroup> groups = createInitialMergeGroups(connectedComponent);
    Collection<Policy> policies = List.of(new VerticalClassMergerPolicy(appView, pinnedClasses));
    groups = run(groups, policies, executorService, timing);
    return new ConnectedComponentVerticalClassMerger(appView, groups);
  }

  @SuppressWarnings("JdkObsolete")
  private LinkedList<VerticalMergeGroup> createInitialMergeGroups(
      Set<DexProgramClass> connectedComponent) {
    LinkedList<VerticalMergeGroup> groups = new LinkedList<>();
    for (DexProgramClass mergeCandidate : connectedComponent) {
      List<DexProgramClass> subclasses = immediateSubtypingInfo.getSubclasses(mergeCandidate);
      if (subclasses.size() == 1) {
        groups.add(new VerticalMergeGroup(mergeCandidate, ListUtils.first(subclasses)));
      }
    }
    return groups;
  }

  @Override
  protected LinkedList<VerticalMergeGroup> apply(
      Policy policy, LinkedList<VerticalMergeGroup> linkedGroups, ExecutorService executorService)
      throws ExecutionException {
    assert policy.isVerticalClassMergerPolicy();
    return apply(policy.asVerticalClassMergerPolicy(), linkedGroups);
  }

  private LinkedList<VerticalMergeGroup> apply(
      VerticalClassMergerPolicy policy, LinkedList<VerticalMergeGroup> linkedGroups) {
    linkedGroups.removeIf(group -> !policy.canMerge(group));
    return linkedGroups;
  }
}
