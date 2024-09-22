// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexDefinition;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.SubtypingInfo;
import com.android.tools.r8.shaking.RootSetUtils.ConsequentRootSetBuilder;
import com.android.tools.r8.shaking.RootSetUtils.RootSetBuilder;
import com.android.tools.r8.shaking.ifrules.MaterializedSubsequentRulesOptimizer;
import com.android.tools.r8.threading.TaskCollection;
import com.android.tools.r8.utils.MapUtils;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.UncheckedExecutionException;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class IfRuleEvaluator {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final DexItemFactory factory;
  private final SubtypingInfo subtypingInfo;
  private final Enqueuer enqueuer;
  private final ConsequentRootSetBuilder rootSetBuilder;
  private final TaskCollection<?> tasks;

  IfRuleEvaluator(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      SubtypingInfo subtypingInfo,
      Enqueuer enqueuer,
      ConsequentRootSetBuilder rootSetBuilder,
      TaskCollection<?> tasks) {
    assert tasks.isEmpty();
    this.appView = appView;
    this.factory = appView.dexItemFactory();
    this.subtypingInfo = subtypingInfo;
    this.enqueuer = enqueuer;
    this.rootSetBuilder = rootSetBuilder;
    this.tasks = tasks;
  }

  public void processActiveIfRulesWithMembers(
      Map<Wrapper<ProguardIfRule>, Set<ProguardIfRule>> ifRules,
      Iterable<DexProgramClass> classesWithNewlyLiveMembers,
      Predicate<DexProgramClass> isEffectivelyLive)
      throws ExecutionException {
    MapUtils.removeIf(
        ifRules,
        (ifRuleWrapper, ifRulesInEquivalence) -> {
          ProguardIfRule ifRuleKey = ifRuleWrapper.get();
          List<ProguardIfRule> toRemove = new ArrayList<>();

          // Depending on which types that trigger the -if rule, the application of the subsequent
          // -keep rule may vary (due to back references). So, we need to try all pairs of -if
          // rule and live types.
          for (DexProgramClass clazz :
              ifRuleKey.relevantCandidatesForRule(
                  appView, subtypingInfo, classesWithNewlyLiveMembers, isEffectivelyLive)) {
            assert isEffectivelyLive.test(clazz);
            processActiveIfRulesWithMembersAndSameClassPrecondition(
                ifRuleKey, ifRulesInEquivalence, clazz, toRemove);
          }
          if (ifRulesInEquivalence.size() == toRemove.size()) {
            return true;
          }
          toRemove.forEach(ifRulesInEquivalence::remove);
          return false;
        });
    tasks.await();
  }

  private void processActiveIfRulesWithMembersAndSameClassPrecondition(
      ProguardIfRule ifRuleKey,
      Set<ProguardIfRule> ifRulesInEquivalence,
      DexProgramClass clazz,
      List<ProguardIfRule> toRemove) {
    // Check if the class matches the if-rule.
    if (!evaluateClassForIfRule(ifRuleKey, clazz)) {
      return;
    }
    // When matching an if rule against a type, the if-rule are filled with the current
    // capture of wildcards. Propagate this down to member rules with same class part
    // equivalence.
    for (ProguardIfRule ifRule : ifRulesInEquivalence) {
      registerClassCapture(ifRule, clazz, clazz);
      List<Pair<ProguardIfRulePreconditionMatch, ProguardKeepRule>> materializedSubsequentRules =
          new ArrayList<>();
      evaluateIfRuleMembersAndMaterialize(
          ifRule,
          clazz,
          (ifRulePreconditionMatch, materializedSubsequentRule) -> {
            materializedSubsequentRules.add(
                new Pair<>(ifRulePreconditionMatch, materializedSubsequentRule));
            if (canRemoveSubsequentKeepRule(ifRule)) {
              toRemove.add(ifRule);
              return true;
            }
            return false;
          });
      applyMaterializedSubsequentRules(ifRule, materializedSubsequentRules);
    }
  }

  public void processActiveIfRulesWithoutMembers(
      Map<Wrapper<ProguardIfRule>, Set<ProguardIfRule>> ifRules,
      Iterable<DexProgramClass> newlyLiveClasses)
      throws ExecutionException {
    MapUtils.removeIf(
        ifRules,
        (ifRuleWrapper, ifRulesInEquivalence) -> {
          // Depending on which types that trigger the -if rule, the application of the subsequent
          // -keep rule may vary (due to back references). So, we need to try all pairs of -if
          // rule and live types.
          ProguardIfRule ifRuleKey = ifRuleWrapper.get();
          List<DexProgramClass> classMatches = new ArrayList<>();
          for (DexProgramClass clazz : newlyLiveClasses) {
            if (evaluateClassForIfRule(ifRuleKey, clazz)) {
              classMatches.add(clazz);
            }
          }
          if (!classMatches.isEmpty()) {
            ifRulesInEquivalence.removeIf(
                ifRule -> processActiveIfRuleWithWithoutMembers(ifRule, classMatches));
          }
          return ifRulesInEquivalence.isEmpty();
        });
    tasks.await();
  }

  /**
   * Returns true if the subsequent rule of {@param ifRule} has been fully evaluated, meaning it can
   * be safely ignored for the remainder of the current tree shaking.
   */
  private boolean processActiveIfRuleWithWithoutMembers(
      ProguardIfRule ifRule, List<DexProgramClass> classMatches) {
    boolean isSubsequentRuleFullyEvaluated = false;
    boolean noBackReferences = canRemoveSubsequentKeepRule(ifRule);
    List<Pair<ProguardIfRulePreconditionMatch, ProguardKeepRule>> materializedSubsequentRules =
        new ArrayList<>();
    for (DexProgramClass clazz : classMatches) {
      registerClassCapture(ifRule, clazz, clazz);
      ProguardIfRulePreconditionMatch ifRulePreconditionMatch =
          new ProguardIfRulePreconditionMatch(ifRule, clazz);
      ifRulePreconditionMatch.disallowOptimizationsForReevaluation(enqueuer, rootSetBuilder);
      ProguardKeepRule materializedSubsequentRule = ifRule.materialize(factory);
      materializedSubsequentRules.add(
          new Pair<>(ifRulePreconditionMatch, materializedSubsequentRule));
      if (noBackReferences) {
        isSubsequentRuleFullyEvaluated = true;
        break;
      }
    }
    applyMaterializedSubsequentRules(ifRule, materializedSubsequentRules);
    return isSubsequentRuleFullyEvaluated;
  }

  private void incrementNumberOfProguardIfRuleClassEvaluations() {
    if (appView.testing().measureProguardIfRuleEvaluations) {
      appView.testing().proguardIfRuleEvaluationData.numberOfProguardIfRuleClassEvaluations++;
    }
  }

  private void incrementNumberOfProguardIfRuleMemberEvaluations() {
    if (appView.testing().measureProguardIfRuleEvaluations) {
      appView.testing().proguardIfRuleEvaluationData.numberOfProguardIfRuleMemberEvaluations++;
    }
  }

  private boolean canRemoveSubsequentKeepRule(ProguardIfRule rule) {
    return !rule.getSubsequentRule().hasBackReferences();
  }

  /**
   * To ensure the matching work correctly going forward, when a class has matched, we have to run
   * the capturing again on the member rule to ensure the wild cards are correctly populated.
   *
   * @param memberRule The member rule to populate with wildcards.
   * @param source The source class.
   * @param target The target class that can be different when we have vertically merged classes.
   */
  private void registerClassCapture(ProguardIfRule memberRule, DexClass source, DexClass target) {
    boolean classNameResult = memberRule.getClassNames().matches(source.type);
    assert classNameResult;
    if (memberRule.hasInheritanceClassName()) {
      boolean inheritanceResult = rootSetBuilder.satisfyInheritanceRule(target, memberRule);
      assert inheritanceResult;
    }
  }

  /** Determines if {@param clazz} satisfies the given if-rule class specification. */
  private boolean evaluateClassForIfRule(ProguardIfRule rule, DexProgramClass clazz) {
    incrementNumberOfProguardIfRuleClassEvaluations();
    if (!RootSetBuilder.satisfyClassType(rule, clazz)) {
      return false;
    }
    if (!RootSetBuilder.satisfyAccessFlag(rule, clazz)) {
      return false;
    }
    AnnotationMatchResult annotationMatchResult = RootSetBuilder.satisfyAnnotation(rule, clazz);
    if (annotationMatchResult == null) {
      return false;
    }
    rootSetBuilder.handleMatchedAnnotation(annotationMatchResult);
    if (!rule.getClassNames().matches(clazz.type)) {
      return false;
    }
    if (rule.hasInheritanceClassName()) {
      // Try another live type since the current one doesn't satisfy the inheritance rule.
      return rootSetBuilder.satisfyInheritanceRule(clazz, rule);
    }
    return true;
  }

  private void evaluateIfRuleMembersAndMaterialize(
      ProguardIfRule rule,
      DexProgramClass clazz,
      BiPredicate<ProguardIfRulePreconditionMatch, ProguardKeepRule> materializedIfRuleConsumer) {
    incrementNumberOfProguardIfRuleMemberEvaluations();
    Collection<ProguardMemberRule> memberKeepRules = rule.getMemberRules();
    assert !memberKeepRules.isEmpty();

    List<DexClassAndField> fieldsInlinedByJavaC = new ArrayList<>();
    Set<DexDefinition> filteredMembers = Sets.newIdentityHashSet();
    Iterables.addAll(
        filteredMembers,
        clazz.fields(
            f -> {
              // Fields that are javac inlined are unsound as predicates for conditional rules.
              // Ignore any such field members and record it for possible reporting later.
              if (isFieldInlinedByJavaC(f)) {
                fieldsInlinedByJavaC.add(DexClassAndField.create(clazz, f));
                return false;
              }
              // Fields referenced only by -keep may not be referenced, we therefore have to
              // filter on both live and referenced.
              return (enqueuer.isFieldLive(f)
                      || enqueuer.isFieldReferenced(f)
                      || f.getOptimizationInfo().valueHasBeenPropagated())
                  && appView
                      .graphLens()
                      .getOriginalFieldSignature(f.getReference())
                      .getHolderType()
                      .isIdenticalTo(clazz.getType());
            }));
    Iterables.addAll(
        filteredMembers,
        clazz.methods(
            m ->
                (enqueuer.isMethodLive(m)
                        || enqueuer.isMethodTargeted(m)
                        || m.getOptimizationInfo().returnValueHasBeenPropagated())
                    && appView
                        .graphLens()
                        .getOriginalMethodSignature(m.getReference())
                        .getHolderType()
                        .isIdenticalTo(clazz.getType())));

    // Check if the rule could hypothetically have matched a javac inlined field.
    // If so mark the rule. Reporting happens only if the rule is otherwise unused.
    if (!fieldsInlinedByJavaC.isEmpty()) {
      for (ProguardMemberRule memberRule : memberKeepRules) {
        if (!memberRule.getRuleType().includesFields()) {
          continue;
        }
        for (DexClassAndField field : fieldsInlinedByJavaC) {
          if (rootSetBuilder.sideEffectFreeIsRuleSatisfiedByField(memberRule, field)) {
            rule.addInlinableFieldMatchingPrecondition(field.getReference());
          }
        }
      }
    }

    // If the number of member rules to hold is more than live members, we can't make it.
    if (filteredMembers.size() < memberKeepRules.size()) {
      return;
    }

    // Depending on which members trigger the -if rule, the application of the subsequent
    // -keep rule may vary (due to back references). So, we need to try literally all
    // combinations of live members. But, we can at least limit the number of elements per
    // combination as the size of member rules to satisfy.
    // TODO(b/206086945): Consider ways of reducing the size of this set computation.
    for (Set<DexDefinition> combination :
        Sets.combinations(filteredMembers, memberKeepRules.size())) {
      Collection<DexClassAndField> fieldsInCombination =
          DexDefinition.filterDexEncodedField(
                  combination.stream(), field -> DexClassAndField.create(clazz, field))
              .collect(Collectors.toList());
      Collection<DexClassAndMethod> methodsInCombination =
          DexDefinition.filterDexEncodedMethod(
                  combination.stream(), method -> DexClassAndMethod.create(clazz, method))
              .collect(Collectors.toList());
      // Member rules are combined as AND logic: if found unsatisfied member rule, this
      // combination of live members is not a good fit.
      ProgramMethodSet methodsSatisfyingRule = ProgramMethodSet.create();
      boolean satisfied =
          memberKeepRules.stream()
              .allMatch(
                  memberRule -> {
                    if (rootSetBuilder.ruleSatisfiedByFields(memberRule, fieldsInCombination)) {
                      return true;
                    }
                    DexClassAndMethod methodSatisfyingRule =
                        rootSetBuilder.getMethodSatisfyingRule(memberRule, methodsInCombination);
                    if (methodSatisfyingRule != null) {
                      methodsSatisfyingRule.add(methodSatisfyingRule.asProgramMethod());
                      return true;
                    }
                    return false;
                  });
      if (satisfied) {
        ProguardIfRulePreconditionMatch ifRulePreconditionMatch =
            new ProguardIfRulePreconditionMatch(rule, clazz, methodsSatisfyingRule);
        ifRulePreconditionMatch.disallowOptimizationsForReevaluation(enqueuer, rootSetBuilder);
        ProguardKeepRule materializedSubsequentRule = rule.materialize(factory);
        if (materializedIfRuleConsumer.test(ifRulePreconditionMatch, materializedSubsequentRule)) {
          return;
        }
      }
    }
  }

  private boolean isFieldInlinedByJavaC(DexEncodedField field) {
    if (enqueuer.getMode().isFinalTreeShaking()) {
      // Ignore any field value in the final tree shaking pass so it remains consistent with the
      // initial pass.
      return field.getIsInlinableByJavaC();
    }
    return field.getOrComputeIsInlinableByJavaC(factory);
  }

  private void applyMaterializedSubsequentRules(
      ProguardIfRule ifRule,
      List<Pair<ProguardIfRulePreconditionMatch, ProguardKeepRule>> materializedSubsequentRules) {
    materializedSubsequentRules =
        MaterializedSubsequentRulesOptimizer.optimize(ifRule, materializedSubsequentRules);
    for (Pair<ProguardIfRulePreconditionMatch, ProguardKeepRule> pair :
        materializedSubsequentRules) {
      ProguardIfRulePreconditionMatch ifRulePreconditionMatch = pair.getFirst();
      ProguardKeepRule materializedSubsequentRule = pair.getSecond();
      applyMaterializedSubsequentRule(materializedSubsequentRule, ifRulePreconditionMatch);
    }
  }

  private void applyMaterializedSubsequentRule(
      ProguardKeepRule materializedSubsequentRule,
      ProguardIfRulePreconditionMatch ifRulePreconditionMatch) {
    try {
      rootSetBuilder.runPerRule(tasks, materializedSubsequentRule, ifRulePreconditionMatch);
    } catch (ExecutionException e) {
      throw new UncheckedExecutionException(e);
    }
  }
}
