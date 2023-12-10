// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.features;

import com.android.tools.r8.FeatureSplit;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMember;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.synthesis.SyntheticItems;

public class FeatureSplitBoundaryOptimizationUtils {

  public static ConstraintWithTarget getInliningConstraintForResolvedMember(
      ProgramMethod method,
      DexClassAndMember<?, ?> resolvedMember,
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    ClassToFeatureSplitMap classToFeatureSplitMap = appView.appInfo().getClassToFeatureSplitMap();
    // We never inline into the base from a feature (calls should never happen) and we never inline
    // between features, so this check should be sufficient.
    if (classToFeatureSplitMap.isInBaseOrSameFeatureAs(
        resolvedMember.getHolderType(), method, appView)) {
      return ConstraintWithTarget.ALWAYS;
    }
    return ConstraintWithTarget.NEVER;
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
    ClassToFeatureSplitMap classToFeatureSplitMap = appView.appInfo().getClassToFeatureSplitMap();
    FeatureSplit callerFeatureSplit = classToFeatureSplitMap.getFeatureSplit(caller, appView);
    FeatureSplit calleeFeatureSplit = classToFeatureSplitMap.getFeatureSplit(callee, appView);

    // First guarantee that we don't cross any actual feature split boundaries.
    if (!calleeFeatureSplit.isBase()) {
      return calleeFeatureSplit == callerFeatureSplit;
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
