// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.rules;

import com.android.tools.r8.shaking.Enqueuer;
import com.android.tools.r8.shaking.MinimumKeepInfoCollection;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

public class ApplicableRulesEvaluatorImpl<T, R extends PendingConditionalRuleBase<T>>
    extends ApplicableRulesEvaluator {

  private final MinimumKeepInfoCollection rootConsequences;

  // TODO(b/323816623): Revaluate these numbers. They are set low/tight now to hit in tests.
  private static final int reallocMinThreshold = 1;
  private static final int reallocRatioThreshold = 10;
  private int prunedCount = 0;
  private List<R> pendingConditionalRules;

  private final List<MaterializedConditionalRule> materializedRules = new ArrayList<>();

  private BiConsumer<R, Enqueuer> onSatisfiedRuleCallback;

  ApplicableRulesEvaluatorImpl(
      MinimumKeepInfoCollection rootConsequences, List<R> conditionalRules) {
    this(rootConsequences, conditionalRules, (unusedRule, unusedEnqueuer) -> {});
  }

  ApplicableRulesEvaluatorImpl(
      MinimumKeepInfoCollection rootConsequences,
      List<R> conditionalRules,
      BiConsumer<R, Enqueuer> onSatisfiedRuleCallback) {
    assert !rootConsequences.isEmpty() || !conditionalRules.isEmpty();
    this.rootConsequences = rootConsequences;
    this.pendingConditionalRules = conditionalRules;
    this.onSatisfiedRuleCallback = onSatisfiedRuleCallback;
  }

  @Override
  public void evaluateUnconditionalRules(Enqueuer enqueuer) {
    assert materializedRules.isEmpty();
    if (!rootConsequences.isEmpty()) {
      enqueuer.includeMinimumKeepInfo(rootConsequences);
    }
  }

  @Override
  public void evaluateConditionalRules(Enqueuer enqueuer) {
    if (pendingConditionalRules.isEmpty()) {
      return;
    }
    // TODO(b/323816623): If we tracked newly live, we could speed up finding rules.
    // TODO(b/323816623): Parallelize this.
    MinimumKeepInfoCollection consequences = null;
    for (int i = 0; i < pendingConditionalRules.size(); i++) {
      R rule = pendingConditionalRules.get(i);
      if (rule != null && rule.isSatisfiedAfterUpdate(enqueuer)) {
        ++prunedCount;
        pendingConditionalRules.set(i, null);
        materializedRules.add(rule.asMaterialized());
        onSatisfiedRuleCallback.accept(rule, enqueuer);
        if (consequences == null) {
          consequences = MinimumKeepInfoCollection.create();
        }
        consequences.merge(rule.getConsequences());
      }
    }
    if (consequences != null) {
      enqueuer.getWorklist().enqueueConditionalRuleConsequencesAction(consequences);
    }

    if (prunedCount == pendingConditionalRules.size()) {
      assert Iterables.all(pendingConditionalRules, Objects::isNull);
      prunedCount = 0;
      pendingConditionalRules = Collections.emptyList();
      return;
    }

    int threshold =
        Math.max(reallocMinThreshold, pendingConditionalRules.size() / reallocRatioThreshold);
    if (prunedCount >= threshold) {
      int newSize = pendingConditionalRules.size() - prunedCount;
      List<R> newPending = new ArrayList<>(newSize);
      for (R rule : pendingConditionalRules) {
        if (rule != null) {
          assert rule.isOutstanding();
          newPending.add(rule);
        }
      }
      assert newPending.size() == newSize;
      prunedCount = 0;
      pendingConditionalRules = newPending;
    }
  }

  @Override
  public MaterializedRules getMaterializedRules() {
    return new MaterializedRules(rootConsequences, materializedRules);
  }
}
