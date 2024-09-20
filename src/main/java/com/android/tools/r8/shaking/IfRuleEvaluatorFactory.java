// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.SubtypingInfo;
import com.android.tools.r8.graph.analysis.EnqueuerAnalysis;
import com.android.tools.r8.shaking.RootSetUtils.ConsequentRootSet;
import com.android.tools.r8.shaking.RootSetUtils.ConsequentRootSetBuilder;
import com.android.tools.r8.threading.TaskCollection;
import com.android.tools.r8.utils.Timing;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class IfRuleEvaluatorFactory extends EnqueuerAnalysis {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;

  /** Map of active if rules. This is important for speeding up aapt2 generated keep rules. */
  private final Map<Wrapper<ProguardIfRule>, Set<ProguardIfRule>> activeIfRulesWithMembers;

  private final Map<Wrapper<ProguardIfRule>, Set<ProguardIfRule>> activeIfRulesWithoutMembers;

  private final Set<DexProgramClass> effectivelyFakeLiveClasses;
  private final Set<DexProgramClass> newlyLiveClasses = Sets.newIdentityHashSet();

  private boolean seenFixpoint;
  private long previousNumberOfLiveItems = 0;

  private final TaskCollection<?> tasks;

  public IfRuleEvaluatorFactory(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Enqueuer enqueuer,
      ExecutorService executorService) {
    this.appView = appView;
    this.activeIfRulesWithMembers = createActiveIfRules(appView.rootSet().ifRules, true);
    this.activeIfRulesWithoutMembers = createActiveIfRules(appView.rootSet().ifRules, false);
    this.effectivelyFakeLiveClasses = createEffectivelyFakeLiveClasses(appView, enqueuer);
    this.tasks = new TaskCollection<>(appView.options(), executorService);
  }

  public static void register(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Enqueuer enqueuer,
      ExecutorService executorService) {
    Set<ProguardIfRule> ifRules =
        appView.hasRootSet() ? appView.rootSet().ifRules : Collections.emptySet();
    if (ifRules != null && !ifRules.isEmpty()) {
      enqueuer.registerAnalysis(new IfRuleEvaluatorFactory(appView, enqueuer, executorService));
    }
  }

  private static Map<Wrapper<ProguardIfRule>, Set<ProguardIfRule>> createActiveIfRules(
      Set<ProguardIfRule> ifRules, boolean withMembers) {
    // Build the mapping of active if rules. We use a single collection of if-rules to allow
    // removing if rules that have a constant sequent keep rule when they materialize.
    Map<Wrapper<ProguardIfRule>, Set<ProguardIfRule>> activeIfRules = new HashMap<>(ifRules.size());
    IfRuleClassPartEquivalence equivalence = new IfRuleClassPartEquivalence();
    for (ProguardIfRule ifRule : ifRules) {
      boolean hasMembers = !ifRule.getMemberRules().isEmpty();
      if (hasMembers == withMembers) {
        Wrapper<ProguardIfRule> wrap = equivalence.wrap(ifRule);
        activeIfRules.computeIfAbsent(wrap, ignoreKey(LinkedHashSet::new)).add(ifRule);
      }
    }
    return activeIfRules;
  }

  @SuppressWarnings("MixedMutabilityReturnType")
  private static Set<DexProgramClass> createEffectivelyFakeLiveClasses(
      AppView<? extends AppInfoWithClassHierarchy> appView, Enqueuer enqueuer) {
    if (enqueuer.getMode().isInitialTreeShaking()) {
      return Collections.emptySet();
    }
    Set<DexProgramClass> effectivelyFakeLiveClasses = Sets.newIdentityHashSet();
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      if (isFakeEffectiveLive(clazz)) {
        effectivelyFakeLiveClasses.add(clazz);
      }
    }
    return effectivelyFakeLiveClasses;
  }

  private static boolean isFakeEffectiveLive(DexProgramClass clazz) {
    // TODO(b/325014359): Replace this by value tracking in instructions (akin to resource values).
    for (DexEncodedField field : clazz.fields()) {
      if (field.getOptimizationInfo().valueHasBeenPropagated()) {
        return true;
      }
    }
    // TODO(b/325014359): Replace this by value or position tracking.
    //  We need to be careful not to throw away such values/positions.
    for (DexEncodedMethod method : clazz.methods()) {
      if (method.getOptimizationInfo().returnValueHasBeenPropagated()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void notifyFixpoint(
      Enqueuer enqueuer, EnqueuerWorklist worklist, ExecutorService executorService, Timing timing)
      throws ExecutionException {
    boolean isFirstFixpoint = setSeenFixpoint();
    if (!shouldProcessActiveIfRulesWithMembers(enqueuer)
        && !shouldProcessActiveIfRulesWithoutMembers(isFirstFixpoint)) {
      return;
    }
    long numberOfLiveItemsAtStart = enqueuer.getNumberOfLiveItems();
    ConsequentRootSet consequentRootSet =
        timing.time(
            "Find consequent items for -if rules...",
            () -> processActiveIfRules(enqueuer, isFirstFixpoint));
    enqueuer.addConsequentRootSet(consequentRootSet);
    long numberOfLiveItemsAtEnd = enqueuer.getNumberOfLiveItems();
    assert numberOfLiveItemsAtEnd == numberOfLiveItemsAtStart;
    previousNumberOfLiveItems = numberOfLiveItemsAtEnd;
  }

  private boolean shouldProcessActiveIfRulesWithMembers(Enqueuer enqueuer) {
    return !activeIfRulesWithMembers.isEmpty()
        && enqueuer.getNumberOfLiveItems() > previousNumberOfLiveItems;
  }

  private ConsequentRootSet processActiveIfRules(Enqueuer enqueuer, boolean isFirstFixpoint)
      throws ExecutionException {
    SubtypingInfo subtypingInfo = enqueuer.getSubtypingInfo();
    ConsequentRootSetBuilder consequentRootSetBuilder =
        ConsequentRootSet.builder(appView, enqueuer, subtypingInfo);
    IfRuleEvaluator evaluator =
        new IfRuleEvaluator(appView, subtypingInfo, enqueuer, consequentRootSetBuilder, tasks);
    if (shouldProcessActiveIfRulesWithMembers(enqueuer)) {
      processActiveIfRulesWithMembers(evaluator);
    }
    if (shouldProcessActiveIfRulesWithoutMembers(isFirstFixpoint)) {
      processActiveIfRulesWithoutMembers(evaluator, isFirstFixpoint);
    }
    return consequentRootSetBuilder.buildConsequentRootSet();
  }

  private void processActiveIfRulesWithMembers(IfRuleEvaluator evaluator)
      throws ExecutionException {
    evaluator.processActiveIfRulesWithMembers(activeIfRulesWithMembers, effectivelyFakeLiveClasses);
  }

  private boolean shouldProcessActiveIfRulesWithoutMembers(boolean isFirstFixpoint) {
    if (activeIfRulesWithoutMembers.isEmpty()) {
      return false;
    }
    if (isFirstFixpoint && !effectivelyFakeLiveClasses.isEmpty()) {
      return true;
    }
    return !newlyLiveClasses.isEmpty();
  }

  private void processActiveIfRulesWithoutMembers(
      IfRuleEvaluator evaluator, boolean isFirstFixpoint) throws ExecutionException {
    if (isFirstFixpoint && !effectivelyFakeLiveClasses.isEmpty()) {
      evaluator.processActiveIfRulesWithoutMembers(
          activeIfRulesWithoutMembers,
          Iterables.concat(effectivelyFakeLiveClasses, newlyLiveClasses));
    } else {
      evaluator.processActiveIfRulesWithoutMembers(activeIfRulesWithoutMembers, newlyLiveClasses);
    }
    newlyLiveClasses.clear();
  }

  private boolean setSeenFixpoint() {
    if (!seenFixpoint) {
      seenFixpoint = true;
      return true;
    }
    return false;
  }

  @Override
  public void processNewlyLiveClass(DexProgramClass clazz, EnqueuerWorklist worklist) {
    if (effectivelyFakeLiveClasses.contains(clazz)) {
      return;
    }
    newlyLiveClasses.add(clazz);
  }
}
