// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging;

import com.android.tools.r8.classmerging.Policy;
import com.android.tools.r8.classmerging.PolicyExecutor;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.timing.Timing;
import com.android.tools.r8.verticalclassmerging.policies.VerticalClassMergerPolicyWithPreprocessing;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class VerticalClassMergerPolicyExecutor extends PolicyExecutor<VerticalMergeGroup> {

  private final AppView<AppInfoWithLiveness> appView;
  private final ImmediateProgramSubtypingInfo immediateSubtypingInfo;

  VerticalClassMergerPolicyExecutor(
      AppView<AppInfoWithLiveness> appView, ImmediateProgramSubtypingInfo immediateSubtypingInfo) {
    this.appView = appView;
    this.immediateSubtypingInfo = immediateSubtypingInfo;
  }

  ConnectedComponentVerticalClassMerger run(
      Set<DexProgramClass> connectedComponent,
      Collection<Policy> policies,
      ExecutorService executorService,
      Timing timing)
      throws ExecutionException {
    Collection<VerticalMergeGroup> groups =
        createInitialMergeGroupsWithDeterministicOrder(connectedComponent);
    groups = run(groups, policies, executorService, timing);
    return new ConnectedComponentVerticalClassMerger(appView, groups);
  }

  private Collection<VerticalMergeGroup> createInitialMergeGroupsWithDeterministicOrder(
      Set<DexProgramClass> connectedComponent) {
    List<VerticalMergeGroup> groups = new ArrayList<>();
    for (DexProgramClass mergeCandidate : connectedComponent) {
      List<DexProgramClass> subclasses = immediateSubtypingInfo.getSubclasses(mergeCandidate);
      if (subclasses.size() == 1) {
        groups.add(new VerticalMergeGroup(mergeCandidate, ListUtils.first(subclasses)));
      }
    }
    return ListUtils.destructiveSort(
        groups, Comparator.comparing(group -> group.getSource().getType()));
  }

  @Override
  protected LinkedList<VerticalMergeGroup> apply(
      Policy policy, LinkedList<VerticalMergeGroup> linkedGroups, ExecutorService executorService)
      throws ExecutionException {
    assert policy.isVerticalClassMergerPolicy();
    return apply(policy.asVerticalClassMergerPolicy(), linkedGroups);
  }

  private <T> LinkedList<VerticalMergeGroup> apply(
      VerticalClassMergerPolicyWithPreprocessing<T> policy,
      LinkedList<VerticalMergeGroup> linkedGroups) {
    T data = policy.preprocess(linkedGroups);
    linkedGroups.removeIf(
        group -> {
          if (policy.canMerge(group, data)) {
            return false;
          }
          assert policy.recordRemovedClassesForDebugging(
              group.getSource().isInterface(), group.size(), Collections.emptyList());
          return true;
        });
    return linkedGroups;
  }
}
