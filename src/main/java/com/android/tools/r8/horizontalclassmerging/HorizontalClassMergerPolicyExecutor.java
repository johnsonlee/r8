// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.classmerging.Policy;
import com.android.tools.r8.classmerging.PolicyExecutor;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class HorizontalClassMergerPolicyExecutor extends PolicyExecutor<HorizontalMergeGroup> {

  @Override
  protected LinkedList<HorizontalMergeGroup> apply(
      Policy policy, LinkedList<HorizontalMergeGroup> linkedGroups, ExecutorService executorService)
      throws ExecutionException {
    if (policy.isSingleClassPolicy()) {
      applySingleClassPolicy(policy.asSingleClassPolicy(), linkedGroups);
    } else {
      if (policy.isMultiClassPolicy()) {
        linkedGroups = applyMultiClassPolicy(policy.asMultiClassPolicy(), linkedGroups);
      } else {
        assert policy.isMultiClassPolicyWithPreprocessing();
        linkedGroups =
            applyMultiClassPolicyWithPreprocessing(
                policy.asMultiClassPolicyWithPreprocessing(), linkedGroups, executorService);
      }
    }
    return linkedGroups;
  }

  void applySingleClassPolicy(SingleClassPolicy policy, LinkedList<HorizontalMergeGroup> groups) {
    Iterator<HorizontalMergeGroup> i = groups.iterator();
    while (i.hasNext()) {
      HorizontalMergeGroup group = i.next();
      boolean isInterfaceGroup = group.isInterfaceGroup();
      int previousGroupSize = group.size();
      group.removeIf(clazz -> !policy.canMerge(clazz));
      assert policy.recordRemovedClassesForDebugging(
          isInterfaceGroup, previousGroupSize, ImmutableList.of(group));
      if (group.isTrivial()) {
        i.remove();
      }
    }
  }

  // TODO(b/270398965): Replace LinkedList.
  @SuppressWarnings("JdkObsolete")
  private LinkedList<HorizontalMergeGroup> applyMultiClassPolicy(
      MultiClassPolicy policy, LinkedList<HorizontalMergeGroup> groups) {
    // For each group apply the multi class policy and add all the new groups together.
    LinkedList<HorizontalMergeGroup> newGroups = new LinkedList<>();
    groups.forEach(
        group -> {
          boolean isInterfaceGroup = group.isInterfaceGroup();
          int previousGroupSize = group.size();
          Collection<HorizontalMergeGroup> policyGroups = policy.apply(group);
          policyGroups.forEach(newGroup -> newGroup.applyMetadataFrom(group));
          assert policy.recordRemovedClassesForDebugging(
              isInterfaceGroup, previousGroupSize, policyGroups);
          newGroups.addAll(policyGroups);
        });
    return newGroups;
  }

  // TODO(b/270398965): Replace LinkedList.
  @SuppressWarnings("JdkObsolete")
  private <T> LinkedList<HorizontalMergeGroup> applyMultiClassPolicyWithPreprocessing(
      MultiClassPolicyWithPreprocessing<T> policy,
      LinkedList<HorizontalMergeGroup> groups,
      ExecutorService executorService)
      throws ExecutionException {
    // For each group apply the multi class policy and add all the new groups together.
    T data = policy.preprocess(groups, executorService);
    LinkedList<HorizontalMergeGroup> newGroups = new LinkedList<>();
    groups.forEach(
        group -> {
          boolean isInterfaceGroup = group.isInterfaceGroup();
          int previousGroupSize = group.size();
          Collection<HorizontalMergeGroup> policyGroups = policy.apply(group, data);
          policyGroups.forEach(newGroup -> newGroup.applyMetadataFrom(group));
          assert policy.recordRemovedClassesForDebugging(
              isInterfaceGroup, previousGroupSize, policyGroups);
          newGroups.addAll(policyGroups);
        });
    return newGroups;
  }
}
