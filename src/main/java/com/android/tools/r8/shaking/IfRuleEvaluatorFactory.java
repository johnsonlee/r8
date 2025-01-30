// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;
import static com.android.tools.r8.utils.MapUtils.newIdentityHashMapFromCollection;
import static com.google.common.base.Predicates.alwaysTrue;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClassKind;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.analysis.EnqueuerAnalysisCollection;
import com.android.tools.r8.graph.analysis.FixpointEnqueuerAnalysis;
import com.android.tools.r8.graph.analysis.NewlyLiveClassEnqueuerAnalysis;
import com.android.tools.r8.graph.analysis.NewlyLiveFieldEnqueuerAnalysis;
import com.android.tools.r8.graph.analysis.NewlyLiveMethodEnqueuerAnalysis;
import com.android.tools.r8.graph.analysis.NewlyReferencedFieldEnqueuerAnalysis;
import com.android.tools.r8.graph.analysis.NewlyTargetedMethodEnqueuerAnalysis;
import com.android.tools.r8.shaking.RootSetUtils.ConsequentRootSet;
import com.android.tools.r8.shaking.RootSetUtils.ConsequentRootSetBuilder;
import com.android.tools.r8.threading.TaskCollection;
import com.android.tools.r8.utils.Timing;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

public class IfRuleEvaluatorFactory
    implements NewlyLiveClassEnqueuerAnalysis,
        NewlyLiveFieldEnqueuerAnalysis,
        NewlyLiveMethodEnqueuerAnalysis,
        NewlyReferencedFieldEnqueuerAnalysis,
        NewlyTargetedMethodEnqueuerAnalysis,
        FixpointEnqueuerAnalysis {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;

  /** Map of active if rules. This is important for speeding up aapt2 generated keep rules. */
  private final Map<Wrapper<ProguardIfRule>, Set<ProguardIfRule>> activeIfRulesWithMembers;

  private final Map<Wrapper<ProguardIfRule>, Set<ProguardIfRule>> activeIfRulesWithoutMembers;

  private final Set<DexProgramClass> effectivelyFakeLiveClasses;
  private final Set<DexProgramClass> newlyLiveClasses = Sets.newIdentityHashSet();
  private final Set<DexProgramClass> classesWithNewlyLiveMembers = Sets.newIdentityHashSet();

  private boolean seenFixpoint;

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
      EnqueuerAnalysisCollection.Builder builder,
      ExecutorService executorService) {
    Set<ProguardIfRule> ifRules =
        appView.hasRootSet() ? appView.rootSet().ifRules : Collections.emptySet();
    if (ifRules != null && !ifRules.isEmpty()) {
      IfRuleEvaluatorFactory factory =
          new IfRuleEvaluatorFactory(appView, enqueuer, executorService);
      builder
          .addNewlyLiveClassAnalysis(factory)
          .addNewlyLiveFieldAnalysis(factory)
          .addNewlyLiveMethodAnalysis(factory)
          .addNewlyReferencedFieldAnalysis(factory)
          .addNewlyTargetedMethodAnalysis(factory)
          .addFixpointAnalysis(factory);
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

  public void applyActiveIfRulesToClasspathClasses(
      ConsequentRootSetBuilder consequentRootSetBuilder, Enqueuer enqueuer, Timing timing)
      throws ExecutionException {
    try (Timing t = timing.begin("Apply if rules to classpath classes")) {
      applyActiveIfRulesToNonProgramClass(
          consequentRootSetBuilder,
          enqueuer,
          appView.app().asDirect().classpathClasses(),
          ClassKind.CLASSPATH,
          timing);
    }
  }

  public void applyActiveIfRulesToLibraryClasses(
      ConsequentRootSetBuilder consequentRootSetBuilder, Enqueuer enqueuer, Timing timing)
      throws ExecutionException {
    if (!appView.testing().applyIfRulesToLibrary) {
      return;
    }
    try (Timing t = timing.begin("Apply if rules to library classes")) {
      applyActiveIfRulesToNonProgramClass(
          consequentRootSetBuilder,
          enqueuer,
          appView.app().asDirect().libraryClasses(),
          ClassKind.LIBRARY,
          timing);
    }
  }

  private <T extends DexClass> void applyActiveIfRulesToNonProgramClass(
      ConsequentRootSetBuilder consequentRootSetBuilder,
      Enqueuer enqueuer,
      Collection<T> classes,
      ClassKind<T> classKind,
      Timing timing)
      throws ExecutionException {
    IfRuleEvaluator evaluator =
        new IfRuleEvaluator(appView, enqueuer, consequentRootSetBuilder, tasks);
    evaluator.processActiveIfRulesWithMembers(
        activeIfRulesWithMembers, classKind, classes, alwaysTrue());
    evaluator.processActiveIfRulesWithoutMembers(
        activeIfRulesWithoutMembers,
        newIdentityHashMapFromCollection(classes, DexClass::getType, Function.identity()),
        timing);
  }

  @Override
  public void notifyFixpoint(
      Enqueuer enqueuer, EnqueuerWorklist worklist, ExecutorService executorService, Timing timing)
      throws ExecutionException {
    boolean isFirstFixpoint = setSeenFixpoint();
    if (isFirstFixpoint) {
      ConsequentRootSetBuilder consequentRootSetBuilder =
          ConsequentRootSet.builder(appView, enqueuer);
      applyActiveIfRulesToClasspathClasses(consequentRootSetBuilder, enqueuer, timing);
      applyActiveIfRulesToLibraryClasses(consequentRootSetBuilder, enqueuer, timing);
      enqueuer.addConsequentRootSet(consequentRootSetBuilder.buildConsequentRootSet());
    }
    if (!shouldProcessActiveIfRulesWithMembers(isFirstFixpoint)
        && !shouldProcessActiveIfRulesWithoutMembers(isFirstFixpoint)) {
      return;
    }
    long numberOfLiveItemsAtStart = enqueuer.getNumberOfLiveItems();
    ConsequentRootSet consequentRootSet =
        timing.time(
            "Find consequent items for -if rules...",
            () -> processActiveIfRules(enqueuer, isFirstFixpoint, timing));
    enqueuer.addConsequentRootSet(consequentRootSet);
    assert enqueuer.getNumberOfLiveItems() == numberOfLiveItemsAtStart;
  }

  private boolean shouldProcessActiveIfRulesWithMembers(boolean isFirstFixpoint) {
    if (activeIfRulesWithMembers.isEmpty()) {
      return false;
    }
    if (isFirstFixpoint && !effectivelyFakeLiveClasses.isEmpty()) {
      return true;
    }
    return !classesWithNewlyLiveMembers.isEmpty();
  }

  private ConsequentRootSet processActiveIfRules(
      Enqueuer enqueuer, boolean isFirstFixpoint, Timing timing) throws ExecutionException {
    ConsequentRootSetBuilder consequentRootSetBuilder =
        ConsequentRootSet.builder(appView, enqueuer);
    IfRuleEvaluator evaluator =
        new IfRuleEvaluator(appView, enqueuer, consequentRootSetBuilder, tasks);
    timing.begin("If rules with members");
    if (shouldProcessActiveIfRulesWithMembers(isFirstFixpoint)) {
      processActiveIfRulesWithMembers(evaluator, isFirstFixpoint);
    }
    timing.end();
    timing.begin("If rules without members");
    if (shouldProcessActiveIfRulesWithoutMembers(isFirstFixpoint)) {
      processActiveIfRulesWithoutMembers(evaluator, isFirstFixpoint, timing);
    }
    timing.end();
    return consequentRootSetBuilder.buildConsequentRootSet();
  }

  private void processActiveIfRulesWithMembers(IfRuleEvaluator evaluator, boolean isFirstFixpoint)
      throws ExecutionException {
    if (isFirstFixpoint && !effectivelyFakeLiveClasses.isEmpty()) {
      evaluator.processActiveIfRulesWithMembers(
          activeIfRulesWithMembers,
          ClassKind.PROGRAM,
          Iterables.concat(effectivelyFakeLiveClasses, classesWithNewlyLiveMembers),
          clazz ->
              effectivelyFakeLiveClasses.contains(clazz)
                  || classesWithNewlyLiveMembers.contains(clazz));
    } else {
      evaluator.processActiveIfRulesWithMembers(
          activeIfRulesWithMembers,
          ClassKind.PROGRAM,
          classesWithNewlyLiveMembers,
          classesWithNewlyLiveMembers::contains);
    }
    classesWithNewlyLiveMembers.clear();
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
      IfRuleEvaluator evaluator, boolean isFirstFixpoint, Timing timing) throws ExecutionException {
    if (isFirstFixpoint && !effectivelyFakeLiveClasses.isEmpty()) {
      Map<DexType, DexProgramClass> newlyLiveClassesMap =
          new IdentityHashMap<>(effectivelyFakeLiveClasses.size() + newlyLiveClasses.size());
      effectivelyFakeLiveClasses.forEach(clazz -> newlyLiveClassesMap.put(clazz.getType(), clazz));
      newlyLiveClasses.forEach(clazz -> newlyLiveClassesMap.put(clazz.getType(), clazz));
      evaluator.processActiveIfRulesWithoutMembers(
          activeIfRulesWithoutMembers, newlyLiveClassesMap, timing);
    } else {
      Map<DexType, DexProgramClass> newlyLiveClassesMap =
          new IdentityHashMap<>(newlyLiveClasses.size());
      newlyLiveClasses.forEach(clazz -> newlyLiveClassesMap.put(clazz.getType(), clazz));
      evaluator.processActiveIfRulesWithoutMembers(
          activeIfRulesWithoutMembers, newlyLiveClassesMap, timing);
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

  @Override
  public void processNewlyLiveField(
      ProgramField field, ProgramDefinition context, EnqueuerWorklist worklist) {
    addClassWithNewlyLiveMembers(field.getHolder());
  }

  @Override
  public void processNewlyReferencedField(ProgramField field) {
    addClassWithNewlyLiveMembers(field.getHolder());
  }

  @Override
  public void processNewlyLiveMethod(
      ProgramMethod method,
      ProgramDefinition context,
      Enqueuer enqueuer,
      EnqueuerWorklist worklist) {
    addClassWithNewlyLiveMembers(method.getHolder());
  }

  @Override
  public void processNewlyTargetedMethod(ProgramMethod method, EnqueuerWorklist worklist) {
    addClassWithNewlyLiveMembers(method.getHolder());
  }

  private void addClassWithNewlyLiveMembers(DexProgramClass clazz) {
    // In the first fixpoint we report all effectively fake live classes as changed.
    if (seenFixpoint || !effectivelyFakeLiveClasses.contains(clazz)) {
      classesWithNewlyLiveMembers.add(clazz);
    }
  }
}
