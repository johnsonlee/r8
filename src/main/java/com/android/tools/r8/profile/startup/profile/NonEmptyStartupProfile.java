// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.startup.profile;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.partial.R8PartialSubCompilationConfiguration;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.MapUtils;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.timing.Timing;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class NonEmptyStartupProfile extends StartupProfile {

  private final Set<DexType> startupClasses;
  private final Map<DexReference, StartupProfileRule> startupRules;

  public NonEmptyStartupProfile(LinkedHashMap<DexReference, StartupProfileRule> startupRules) {
    assert !startupRules.isEmpty();
    this.startupClasses =
        SetUtils.unmodifiableForTesting(
            SetUtils.mapIdentityHashSet(startupRules.keySet(), DexReference::getContextType));
    this.startupRules = MapUtils.unmodifiableForTesting(startupRules);
  }

  @Override
  public boolean containsClassRule(DexType type) {
    return startupRules.containsKey(type);
  }

  @Override
  public boolean containsMethodRule(DexMethod method) {
    return startupRules.containsKey(method);
  }

  @Override
  public <E extends Exception> void forEachRule(
      ThrowingConsumer<? super StartupProfileRule, E> consumer) throws E {
    forEachRule(consumer, consumer);
  }

  @Override
  public <E1 extends Exception, E2 extends Exception> void forEachRule(
      ThrowingConsumer<? super StartupProfileClassRule, E1> classRuleConsumer,
      ThrowingConsumer<? super StartupProfileMethodRule, E2> methodRuleConsumer)
      throws E1, E2 {
    for (StartupProfileRule rule : startupRules.values()) {
      rule.accept(classRuleConsumer, methodRuleConsumer);
    }
  }

  @Override
  public StartupProfileClassRule getClassRule(DexType type) {
    return (StartupProfileClassRule) startupRules.get(type);
  }

  @Override
  public StartupProfileMethodRule getMethodRule(DexMethod method) {
    return (StartupProfileMethodRule) startupRules.get(method);
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public boolean isStartupClass(DexType type) {
    return startupClasses.contains(type);
  }

  @Override
  public StartupProfile rewrittenWithLens(GraphLens graphLens, Timing timing) {
    return timing.time("Rewrite NonEmptyStartupProfile", () -> rewrittenWithLens(graphLens));
  }

  private StartupProfile rewrittenWithLens(GraphLens graphLens) {
    return transform(
        (classRule, builder) ->
            builder.addClassRule(
                StartupProfileClassRule.builder()
                    .setClassReference(graphLens.lookupType(classRule.getReference()))
                    .build()),
        (methodRule, builder) ->
            builder.addMethodRule(
                StartupProfileMethodRule.builder()
                    .setMethod(graphLens.getRenamedMethodSignature(methodRule.getReference()))
                    .build()));
  }

  public int size() {
    return startupRules.size();
  }

  @Override
  public Builder toEmptyBuilderWithCapacity() {
    return builderWithCapacity(size());
  }

  /**
   * This is called to process the startup profile before computing the startup layouts.
   *
   * <p>This processing makes the following key change to the startup profile: A {@link
   * StartupProfileClassRule} is inserted for all supertypes of a given class next to the class in
   * the startup profile. This ensures that the classes from the super hierarchy will be laid out
   * close to their subclasses, at the point where the subclasses are used during startup.
   *
   * <p>This normally follows from the trace already, except that the class initializers of
   * interfaces are not executed when a subclass is used.
   */
  @Override
  public StartupProfile toStartupProfileForWriting(AppView<?> appView) {
    return toProfileWithSuperclasses(appView);
  }

  @Override
  public StartupProfile withoutMissingItems(AppView<?> appView) {
    AppInfo appInfo = appView.appInfo();
    return transform(
        (classRule, builder) -> {
          if (hasDefinitionFor(appInfo, classRule.getReference())) {
            builder.addClassRule(classRule);
          }
        },
        (methodRule, builder) -> {
          if (hasDefinitionFor(appInfo, methodRule.getReference())) {
            builder.addMethodRule(methodRule);
          }
        });
  }

  private boolean hasDefinitionFor(AppInfo appInfo, DexReference reference) {
    if (appInfo.hasDefinitionForWithoutExistenceAssert(reference)) {
      return true;
    }
    R8PartialSubCompilationConfiguration subCompilationConfiguration =
        appInfo.options().partialSubCompilationConfiguration;
    return subCompilationConfiguration != null
        && subCompilationConfiguration.isR8()
        && subCompilationConfiguration.asR8().hasD8DefinitionFor(reference);
  }

  @Override
  public StartupProfile withoutPrunedItems(
      PrunedItems prunedItems, SyntheticItems syntheticItems, Timing timing) {
    timing.begin("Prune NonEmptyStartupProfile");
    StartupProfile result =
        transform(
            (classRule, builder) -> {
              if (!prunedItems.isRemoved(classRule.getReference())) {
                builder.addClassRule(classRule);
              }
            },
            (methodRule, builder) -> {
              if (!prunedItems.isRemoved(methodRule.getReference())) {
                builder.addMethodRule(methodRule);
              }
            });
    timing.end();
    return result;
  }
}
