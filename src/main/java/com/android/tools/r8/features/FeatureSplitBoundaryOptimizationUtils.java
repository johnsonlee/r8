// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.features;

import static com.android.tools.r8.graph.DexEncodedMethod.CompilationState.PROCESSED_INLINING_CANDIDATE_ANY;

import com.android.tools.r8.FeatureSplit;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMember;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramMember;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.synthesis.SyntheticItems;

public class FeatureSplitBoundaryOptimizationUtils {

  public static ConstraintWithTarget getInliningConstraintForResolvedMember(
      DexClassAndMember<?, ?> resolvedMember,
      DexClass initialResolutionHolder,
      ProgramMethod context,
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    if (!appView.options().hasFeatureSplitConfiguration()) {
      return ConstraintWithTarget.ALWAYS;
    }
    // If we resolve to something outside the program then everything is fine.
    if (!initialResolutionHolder.isProgramClass()) {
      assert !resolvedMember.isProgramMember();
      return ConstraintWithTarget.ALWAYS;
    }
    ProgramMember<?, ?> resolvedProgramMember = resolvedMember.asProgramMember();
    DexProgramClass initialProgramResolutionHolder = initialResolutionHolder.asProgramClass();
    return getInliningConstraintForResolvedMember(
        resolvedProgramMember, initialProgramResolutionHolder, context, appView);
  }

  private static ConstraintWithTarget getInliningConstraintForResolvedMember(
      ProgramMember<?, ?> resolvedMember,
      DexProgramClass initialResolutionHolder,
      ProgramMethod context,
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    assert appView.options().hasFeatureSplitConfiguration();
    ClassToFeatureSplitMap features = appView.appInfo().getClassToFeatureSplitMap();
    // Check that whatever we resolve to is in the same feature or in base.
    if (!features.isInBaseOrSameFeatureAs(initialResolutionHolder, context, appView)
        || !features.isInBaseOrSameFeatureAs(resolvedMember, context, appView)) {
      return ConstraintWithTarget.NEVER;
    }
    // If isolated splits are enabled then the resolved method must be in the same feature split or
    // it must be public.
    if (appView.options().getFeatureSplitConfiguration().isIsolatedSplitsEnabled()) {
      if (!initialResolutionHolder.isPublic()
          && !features.isInSameFeature(initialResolutionHolder, context, appView)) {
        return ConstraintWithTarget.NEVER;
      }
      if (!resolvedMember.getAccessFlags().isPublic()
          && !features.isInSameFeature(resolvedMember, context, appView)) {
        return ConstraintWithTarget.NEVER;
      }
    }
    return ConstraintWithTarget.ALWAYS;
  }

  public static FeatureSplit getMergeKeyForHorizontalClassMerging(
      DexProgramClass clazz, AppView<? extends AppInfoWithClassHierarchy> appView) {
    ClassToFeatureSplitMap classToFeatureSplitMap = appView.appInfo().getClassToFeatureSplitMap();
    return classToFeatureSplitMap.getFeatureSplit(clazz, appView);
  }

  public static boolean isSafeForAccess(
      DexProgramClass accessedClass,
      ProgramDefinition accessor,
      ClassToFeatureSplitMap classToFeatureSplitMap,
      SyntheticItems syntheticItems) {
    return classToFeatureSplitMap.isInBaseOrSameFeatureAs(accessedClass, accessor, syntheticItems);
  }

  public static boolean isSafeForInlining(
      ProgramMethod caller,
      ProgramMethod callee,
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    if (!appView.options().hasFeatureSplitConfiguration()) {
      return true;
    }
    ClassToFeatureSplitMap classToFeatureSplitMap = appView.appInfo().getClassToFeatureSplitMap();
    if (classToFeatureSplitMap.isInSameFeature(caller, callee, appView)) {
      return true;
    }
    // Check that we don't cross any actual feature split boundaries.
    if (!classToFeatureSplitMap.isInBase(callee, appView)) {
      return false;
    }
    // Check that inlining can't lead to accessing a package-private item in base when using
    // isolated splits.
    if (appView.options().getFeatureSplitConfiguration().isIsolatedSplitsEnabled()
        && callee.getDefinition().getCompilationState() != PROCESSED_INLINING_CANDIDATE_ANY) {
      return false;
    }
    return true;
  }

  public static boolean isSafeForVerticalClassMerging(
      DexProgramClass sourceClass,
      DexProgramClass targetClass,
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    ClassToFeatureSplitMap classToFeatureSplitMap = appView.appInfo().getClassToFeatureSplitMap();
    FeatureSplit sourceFeatureSplit = classToFeatureSplitMap.getFeatureSplit(sourceClass, appView);
    FeatureSplit targetFeatureSplit = classToFeatureSplitMap.getFeatureSplit(targetClass, appView);

    // First guarantee that we don't cross any actual feature split boundaries.
    if (targetFeatureSplit.isBase()) {
      assert sourceFeatureSplit.isBase() : "Unexpected class in base that inherits from feature";
      return true;
    } else {
      return sourceFeatureSplit == targetFeatureSplit;
    }
  }
}
