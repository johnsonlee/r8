// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.classmerging.Policy;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.horizontalclassmerging.policies.CheckAbstractClasses;
import com.android.tools.r8.horizontalclassmerging.policies.CheckSyntheticClasses;
import com.android.tools.r8.horizontalclassmerging.policies.FinalizeMergeGroup;
import com.android.tools.r8.horizontalclassmerging.policies.LimitClassGroups;
import com.android.tools.r8.horizontalclassmerging.policies.LimitInterfaceGroups;
import com.android.tools.r8.horizontalclassmerging.policies.MinimizeInstanceFieldCasts;
import com.android.tools.r8.horizontalclassmerging.policies.NoAnnotationClasses;
import com.android.tools.r8.horizontalclassmerging.policies.NoApiOutlineWithNonApiOutline;
import com.android.tools.r8.horizontalclassmerging.policies.NoCheckDiscard;
import com.android.tools.r8.horizontalclassmerging.policies.NoClassAnnotationCollisions;
import com.android.tools.r8.horizontalclassmerging.policies.NoClassInitializerCycles;
import com.android.tools.r8.horizontalclassmerging.policies.NoClassInitializerWithObservableSideEffects;
import com.android.tools.r8.horizontalclassmerging.policies.NoDeadLocks;
import com.android.tools.r8.horizontalclassmerging.policies.NoDefaultInterfaceMethodCollisions;
import com.android.tools.r8.horizontalclassmerging.policies.NoDefaultInterfaceMethodMerging;
import com.android.tools.r8.horizontalclassmerging.policies.NoDifferentApiReferenceLevel;
import com.android.tools.r8.horizontalclassmerging.policies.NoDirectRuntimeTypeChecks;
import com.android.tools.r8.horizontalclassmerging.policies.NoEnums;
import com.android.tools.r8.horizontalclassmerging.policies.NoFailedResolutionTargets;
import com.android.tools.r8.horizontalclassmerging.policies.NoIndirectRuntimeTypeChecks;
import com.android.tools.r8.horizontalclassmerging.policies.NoInnerClasses;
import com.android.tools.r8.horizontalclassmerging.policies.NoInstanceFieldAnnotations;
import com.android.tools.r8.horizontalclassmerging.policies.NoInterfaces;
import com.android.tools.r8.horizontalclassmerging.policies.NoKeepRules;
import com.android.tools.r8.horizontalclassmerging.policies.NoKotlinMetadata;
import com.android.tools.r8.horizontalclassmerging.policies.NoMethodHandleFromLambda;
import com.android.tools.r8.horizontalclassmerging.policies.NoNativeMethods;
import com.android.tools.r8.horizontalclassmerging.policies.NoRecords;
import com.android.tools.r8.horizontalclassmerging.policies.NoResourceClasses;
import com.android.tools.r8.horizontalclassmerging.policies.NoServiceLoaders;
import com.android.tools.r8.horizontalclassmerging.policies.NoWeakerAccessPrivileges;
import com.android.tools.r8.horizontalclassmerging.policies.NotMatchedByNoHorizontalClassMerging;
import com.android.tools.r8.horizontalclassmerging.policies.NotTwoInitsWithMonitors;
import com.android.tools.r8.horizontalclassmerging.policies.OnlyClassesWithStaticDefinitionsAndNoClassInitializer;
import com.android.tools.r8.horizontalclassmerging.policies.OnlyDirectlyConnectedOrUnrelatedInterfaces;
import com.android.tools.r8.horizontalclassmerging.policies.PreserveInterfaceMethodDispatch;
import com.android.tools.r8.horizontalclassmerging.policies.PreserveMethodCharacteristics;
import com.android.tools.r8.horizontalclassmerging.policies.PreventClassMethodAndDefaultMethodCollisions;
import com.android.tools.r8.horizontalclassmerging.policies.RespectPackageBoundaries;
import com.android.tools.r8.horizontalclassmerging.policies.SameFeatureSplit;
import com.android.tools.r8.horizontalclassmerging.policies.SameFilePolicy;
import com.android.tools.r8.horizontalclassmerging.policies.SameInstanceFields;
import com.android.tools.r8.horizontalclassmerging.policies.SameMainDexGroup;
import com.android.tools.r8.horizontalclassmerging.policies.SameNestHost;
import com.android.tools.r8.horizontalclassmerging.policies.SamePackageForNonGlobalMergeSynthetic;
import com.android.tools.r8.horizontalclassmerging.policies.SameParentClass;
import com.android.tools.r8.horizontalclassmerging.policies.SamePartialSubCompilation;
import com.android.tools.r8.horizontalclassmerging.policies.SameStartupPartition;
import com.android.tools.r8.horizontalclassmerging.policies.SyntheticItemsPolicy;
import com.android.tools.r8.horizontalclassmerging.policies.VerifyMultiClassPolicyAlwaysSatisfied;
import com.android.tools.r8.horizontalclassmerging.policies.VerifySingleClassPolicyAlwaysSatisfied;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.RuntimeTypeCheckInfo;
import com.android.tools.r8.utils.ListUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;

public class PolicyScheduler {

  public static List<Policy> getPolicies(
      AppView<?> appView,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo,
      RuntimeTypeCheckInfo runtimeTypeCheckInfo) {
    if (appView.hasClassHierarchy()) {
      return getPoliciesForR8(
          appView.withClassHierarchy(), immediateSubtypingInfo, runtimeTypeCheckInfo);
    } else {
      return getPoliciesForD8(appView.withoutClassHierarchy());
    }
  }

  private static List<Policy> getPoliciesForD8(AppView<AppInfo> appView) {
    List<Policy> policies =
        ImmutableList.<Policy>builder()
            .addAll(getSingleClassPoliciesForD8(appView))
            .addAll(getMultiClassPoliciesForD8(appView))
            .build();
    policies = appView.options().testing.horizontalClassMergingPolicyRewriter.apply(policies);
    assert verifyPolicyOrderingConstraints(policies);
    return policies;
  }

  private static List<Policy> getPoliciesForR8(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo,
      RuntimeTypeCheckInfo runtimeTypeCheckInfo) {
    List<Policy> policies =
        ImmutableList.<Policy>builder()
            .addAll(getSingleClassPolicies(appView, runtimeTypeCheckInfo))
            .addAll(getMultiClassPolicies(appView, immediateSubtypingInfo, runtimeTypeCheckInfo))
            .build();
    policies = appView.options().testing.horizontalClassMergingPolicyRewriter.apply(policies);
    assert verifyPolicyOrderingConstraints(policies);
    return policies;
  }

  private static List<SingleClassPolicy> getSingleClassPolicies(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      RuntimeTypeCheckInfo runtimeTypeCheckInfo) {
    ImmutableList.Builder<SingleClassPolicy> builder = ImmutableList.builder();

    addRequiredSingleClassPolicies(appView, builder);

    if (appView.options().horizontalClassMergerOptions().isRestrictedToSynthetics()) {
      assert verifySingleClassPoliciesIrrelevantForMergingSynthetics(appView, builder);
    } else {
      AppView<AppInfoWithLiveness> appViewWithLiveness = appView.withLiveness();
      addSingleClassPoliciesForMergingNonSyntheticClasses(
          appViewWithLiveness, runtimeTypeCheckInfo, builder);
    }

    return builder.build();
  }

  private static List<SingleClassPolicy> getSingleClassPoliciesForD8(AppView<AppInfo> appView) {
    ImmutableList.Builder<SingleClassPolicy> builder =
        ImmutableList.<SingleClassPolicy>builder()
            .add(new CheckSyntheticClasses(appView))
            .add(new OnlyClassesWithStaticDefinitionsAndNoClassInitializer());
    assert verifySingleClassPoliciesIrrelevantForMergingSyntheticsInD8(appView, builder);
    return builder.build();
  }

  private static void addRequiredSingleClassPolicies(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ImmutableList.Builder<SingleClassPolicy> builder) {
    builder.add(
        new CheckSyntheticClasses(appView),
        new NoCheckDiscard(appView),
        new NoKeepRules(appView),
        new NoClassInitializerWithObservableSideEffects());
    if (appView.hasLiveness() && appView.options().isGeneratingClassFiles()) {
      builder.add(new NoMethodHandleFromLambda(appView.withLiveness()));
    }
  }

  private static void addSingleClassPoliciesForMergingNonSyntheticClasses(
      AppView<AppInfoWithLiveness> appView,
      RuntimeTypeCheckInfo runtimeTypeCheckInfo,
      ImmutableList.Builder<SingleClassPolicy> builder) {
    builder.add(
        new NoResourceClasses(),
        new NotMatchedByNoHorizontalClassMerging(appView),
        new NoAnnotationClasses(),
        new NoDirectRuntimeTypeChecks(appView, runtimeTypeCheckInfo),
        new NoEnums(appView),
        new NoFailedResolutionTargets(appView),
        new NoInterfaces(appView),
        new NoInnerClasses(),
        new NoInstanceFieldAnnotations(),
        new NoKotlinMetadata(),
        new NoNativeMethods(),
        new NoServiceLoaders(appView),
        new NoRecords());
  }

  private static boolean verifySingleClassPoliciesIrrelevantForMergingSynthetics(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ImmutableList.Builder<SingleClassPolicy> builder) {
    List<SingleClassPolicy> policies =
        ImmutableList.of(
            new NoResourceClasses(),
            new NoAnnotationClasses(),
            new NoDirectRuntimeTypeChecks(appView),
            new NoEnums(appView),
            new NoInterfaces(appView),
            new NoInnerClasses(),
            new NoInstanceFieldAnnotations(),
            new NoKotlinMetadata(),
            new NoNativeMethods(),
            new NoServiceLoaders(appView),
            new NoRecords());
    policies.stream().map(VerifySingleClassPolicyAlwaysSatisfied::new).forEach(builder::add);
    return true;
  }

  private static boolean verifySingleClassPoliciesIrrelevantForMergingSyntheticsInD8(
      AppView<AppInfo> appView,
      ImmutableList.Builder<SingleClassPolicy> builder) {
    List<SingleClassPolicy> policies =
        ImmutableList.of(
            new NoResourceClasses(),
            new NoAnnotationClasses(),
            new NoDirectRuntimeTypeChecks(appView),
            new NoInterfaces(appView),
            new NoInnerClasses(),
            new NoInstanceFieldAnnotations(),
            new NoKotlinMetadata(),
            new NoNativeMethods(),
            new NoRecords());
    policies.stream().map(VerifySingleClassPolicyAlwaysSatisfied::new).forEach(builder::add);
    return true;
  }

  private static List<Policy> getMultiClassPolicies(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo,
      RuntimeTypeCheckInfo runtimeTypeCheckInfo) {
    ImmutableList.Builder<Policy> builder = ImmutableList.builder();
    addRequiredMultiClassPolicies(appView, runtimeTypeCheckInfo, builder);
    if (!appView.options().horizontalClassMergerOptions().isRestrictedToSynthetics()) {
      AppView<AppInfoWithLiveness> appViewWithLiveness = appView.withLiveness();
      addMultiClassPoliciesForMergingNonSyntheticClasses(appViewWithLiveness, builder);
    }
    if (appView.hasLiveness()) {
      builder.add(new PreserveMethodCharacteristics(appView.withLiveness()));
    }
    builder.add(new MinimizeInstanceFieldCasts());
    addMultiClassPoliciesForInterfaceMerging(appView, immediateSubtypingInfo, builder);
    return builder.add(new LimitClassGroups(appView), new FinalizeMergeGroup(appView)).build();
  }

  private static List<? extends Policy> getMultiClassPoliciesForD8(AppView<AppInfo> appView) {
    ImmutableList.Builder<MultiClassPolicy> builder = ImmutableList.builder();
    if (appView.options().partialSubCompilationConfiguration != null) {
      builder.add(new SamePartialSubCompilation(appView));
    }
    builder.add(
        new CheckAbstractClasses(appView),
        new SameFeatureSplit(appView),
        new SameMainDexGroup(appView),
        new SameNestHost(appView),
        new SameParentClass(),
        new SyntheticItemsPolicy(appView),
        new NoApiOutlineWithNonApiOutline(appView),
        new SamePackageForNonGlobalMergeSynthetic(appView),
        new NoDifferentApiReferenceLevel(appView),
        new LimitClassGroups(appView));
    assert verifyMultiClassPoliciesIrrelevantForMergingSyntheticsInD8(appView, builder);
    builder.add(new FinalizeMergeGroup(appView));
    return builder.build();
  }

  private static void addRequiredMultiClassPolicies(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      RuntimeTypeCheckInfo runtimeTypeCheckInfo,
      ImmutableList.Builder<Policy> builder) {
    ImmediateProgramSubtypingInfo immediateSubtypingInfo =
        ImmediateProgramSubtypingInfo.create(appView);
    builder.add(
        new SameFilePolicy(appView),
        new CheckAbstractClasses(appView),
        new NoClassAnnotationCollisions(),
        new SameFeatureSplit(appView),
        new SameStartupPartition(appView),
        new SameInstanceFields(appView),
        new SameMainDexGroup(appView),
        new SameNestHost(appView),
        new SameParentClass(),
        new SyntheticItemsPolicy(appView),
        new RespectPackageBoundaries(appView),
        new NoDifferentApiReferenceLevel(appView),
        new NoIndirectRuntimeTypeChecks(appView, runtimeTypeCheckInfo),
        new NoWeakerAccessPrivileges(appView, immediateSubtypingInfo),
        new PreventClassMethodAndDefaultMethodCollisions(appView, immediateSubtypingInfo),
        new PreserveInterfaceMethodDispatch(appView, immediateSubtypingInfo),
        new NotTwoInitsWithMonitors(appView));
  }

  private static void addMultiClassPoliciesForMergingNonSyntheticClasses(
      AppView<AppInfoWithLiveness> appView,
      ImmutableList.Builder<Policy> builder) {
    builder.add(new NoClassInitializerCycles(appView), new NoDeadLocks(appView));
  }

  private static void addMultiClassPoliciesForInterfaceMerging(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo,
      ImmutableList.Builder<Policy> builder) {
    builder.add(
        new NoDefaultInterfaceMethodMerging(appView),
        new NoDefaultInterfaceMethodCollisions(appView, immediateSubtypingInfo),
        new LimitInterfaceGroups(appView),
        new OnlyDirectlyConnectedOrUnrelatedInterfaces(appView, immediateSubtypingInfo));
  }

  private static boolean verifyMultiClassPoliciesIrrelevantForMergingSyntheticsInD8(
      AppView<AppInfo> appView,
      ImmutableList.Builder<MultiClassPolicy> builder) {
    List<MultiClassPolicy> policies =
        ImmutableList.of(new SyntheticItemsPolicy(appView), new SameParentClass());
    policies.stream().map(VerifyMultiClassPolicyAlwaysSatisfied::new).forEach(builder::add);
    return true;
  }

  private static boolean verifyPolicyOrderingConstraints(List<Policy> policies) {
    // No policies that may split interface groups are allowed to run after the
    // OnlyDirectlyConnectedOrUnrelatedInterfaces policy. This policy ensures that interface merging
    // does not lead to any cycles in the interface hierarchy, which may be invalidated if merge
    // groups are split after the policy has run.
    int onlyDirectlyConnectedOrUnrelatedInterfacesIndex =
        ListUtils.lastIndexMatching(
            policies, policy -> policy instanceof OnlyDirectlyConnectedOrUnrelatedInterfaces);
    if (onlyDirectlyConnectedOrUnrelatedInterfacesIndex >= 0) {
      for (Policy successorPolicy :
          policies.subList(onlyDirectlyConnectedOrUnrelatedInterfacesIndex + 1, policies.size())) {
        assert successorPolicy.isIdentityForInterfaceGroups();
      }
    }
    return true;
  }
}
