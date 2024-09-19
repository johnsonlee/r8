// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.SubtypingInfo;
import com.android.tools.r8.shaking.RootSetUtils.ConsequentRootSet;
import com.android.tools.r8.shaking.RootSetUtils.ConsequentRootSetBuilder;
import com.android.tools.r8.threading.TaskCollection;
import com.android.tools.r8.utils.Timing;
import com.google.common.base.Equivalence.Wrapper;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class IfRuleEvaluatorFactory {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final Enqueuer enqueuer;

  /** Map of active if rules. This is important for speeding up aapt2 generated keep rules. */
  private final Map<Wrapper<ProguardIfRule>, Set<ProguardIfRule>> activeIfRules;

  private final TaskCollection<?> tasks;

  public IfRuleEvaluatorFactory(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Enqueuer enqueuer,
      ExecutorService executorService) {
    this.appView = appView;
    this.enqueuer = enqueuer;
    this.activeIfRules =
        createActiveIfRules(
            appView.hasRootSet() ? appView.rootSet().ifRules : Collections.emptySet());
    this.tasks = new TaskCollection<>(appView.options(), executorService);
  }

  @SuppressWarnings("MixedMutabilityReturnType")
  private static Map<Wrapper<ProguardIfRule>, Set<ProguardIfRule>> createActiveIfRules(
      Set<ProguardIfRule> ifRules) {
    if (ifRules == null || ifRules.isEmpty()) {
      return Collections.emptyMap();
    }
    // Build the mapping of active if rules. We use a single collection of if-rules to allow
    // removing if rules that have a constant sequent keep rule when they materialize.
    Map<Wrapper<ProguardIfRule>, Set<ProguardIfRule>> activeIfRules = new HashMap<>(ifRules.size());
    IfRuleClassPartEquivalence equivalence = new IfRuleClassPartEquivalence();
    for (ProguardIfRule ifRule : ifRules) {
      Wrapper<ProguardIfRule> wrap = equivalence.wrap(ifRule);
      activeIfRules.computeIfAbsent(wrap, ignoreKey(LinkedHashSet::new)).add(ifRule);
    }
    return activeIfRules;
  }

  public void run(SubtypingInfo subtypingInfo, Timing timing) throws ExecutionException {
    if (activeIfRules.isEmpty()) {
      return;
    }
    ConsequentRootSetBuilder consequentRootSetBuilder =
        ConsequentRootSet.builder(appView, enqueuer, subtypingInfo);
    IfRuleEvaluator evaluator =
        new IfRuleEvaluator(appView, subtypingInfo, enqueuer, consequentRootSetBuilder, tasks);
    timing.time("Find consequent items for -if rules...", () -> evaluator.run(activeIfRules));
    enqueuer.addConsequentRootSet(consequentRootSetBuilder.buildConsequentRootSet());
  }
}
