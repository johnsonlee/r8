// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging;

import com.android.tools.r8.utils.timing.Timing;
import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * This is a simple policy executor that ensures regular sequential execution of policies. It should
 * primarily be readable and correct. The SimplePolicyExecutor should be a reference implementation,
 * against which more efficient policy executors can be compared.
 */
public abstract class PolicyExecutor<MG extends MergeGroup> {

  /**
   * Given an initial collection of class groups which can potentially be merged, run all of the
   * policies registered to this policy executor on the class groups yielding a new collection of
   * class groups.
   */
  // TODO(b/270398965): Replace LinkedList.
  @SuppressWarnings("JdkObsolete")
  public Collection<MG> run(
      Collection<MG> inputGroups,
      Collection<? extends Policy> policies,
      ExecutorService executorService,
      Timing timing)
      throws ExecutionException {
    LinkedList<MG> linkedGroups;

    if (inputGroups instanceof LinkedList) {
      linkedGroups = (LinkedList<MG>) inputGroups;
    } else {
      linkedGroups = new LinkedList<>(inputGroups);
    }

    for (Policy policy : policies) {
      if (policy.shouldSkipPolicy()) {
        continue;
      }

      timing.begin(policy.getName());
      linkedGroups = apply(policy, linkedGroups, executorService);
      timing.end();

      policy.clear();

      if (linkedGroups.isEmpty()) {
        break;
      }

      // Any policy should not return any trivial groups.
      assert linkedGroups.stream().allMatch(group -> group.size() >= 2);
    }

    return linkedGroups;
  }

  protected abstract LinkedList<MG> apply(
      Policy policy, LinkedList<MG> linkedGroups, ExecutorService executorService)
      throws ExecutionException;
}
