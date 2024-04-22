// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.rules;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.shaking.Enqueuer;
import com.android.tools.r8.shaking.MinimumKeepInfoCollection;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;

public abstract class ApplicableRulesEvaluator {

  public static ApplicableRulesEvaluator empty() {
    return EmptyEvaluator.INSTANCE;
  }

  public abstract void evaluateUnconditionalRules(Enqueuer enqueuer);

  public abstract void evaluateConditionalRules(Enqueuer enqueuer);

  public abstract MaterializedRules getMaterializedRules();

  public static Builder initialRulesBuilder() {
    return new Builder();
  }

  private static class EmptyEvaluator extends ApplicableRulesEvaluator {

    private static EmptyEvaluator INSTANCE = new EmptyEvaluator();

    private EmptyEvaluator() {}

    @Override
    public void evaluateUnconditionalRules(Enqueuer enqueuer) {
      // Nothing to do.
    }

    @Override
    public void evaluateConditionalRules(Enqueuer enqueuer) {
      // Nothing to do.
    }

    @Override
    public MaterializedRules getMaterializedRules() {
      return MaterializedRules.empty();
    }
  }

  public static class Builder {

    private MinimumKeepInfoCollection rootConsequences =
        MinimumKeepInfoCollection.createConcurrent();
    private ConcurrentLinkedDeque<PendingInitialConditionalRule> rules =
        new ConcurrentLinkedDeque<>();

    private Builder() {}

    public void addRootRule(Consumer<MinimumKeepInfoCollection> fn) {
      fn.accept(rootConsequences);
    }

    public void addConditionalRule(PendingInitialConditionalRule rule) {
      rules.add(rule);
    }

    public ApplicableRulesEvaluator build(AppView<? extends AppInfoWithClassHierarchy> appView) {
      if (rootConsequences.isEmpty() && rules.isEmpty()) {
        return ApplicableRulesEvaluator.empty();
      }
      return new ApplicableRulesEvaluatorImpl<>(
          rootConsequences,
          new ArrayList<>(rules),
          (rule, enqueuer) -> {
            // When evaluating the initial rules, if a satisfied rule has a field precondition,
            // mark it to maintain its original field witness.
            for (ProgramDefinition precondition : rule.getSatisfiedPreconditions()) {
              if (precondition.isProgramField()) {
                precondition.asProgramField().recordOriginalFieldWitness(appView);
              }
            }
          });
    }
  }
}
