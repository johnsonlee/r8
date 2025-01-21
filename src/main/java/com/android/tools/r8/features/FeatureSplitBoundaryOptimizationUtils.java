// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.features;

import static com.android.tools.r8.graph.DexEncodedMethod.CompilationState.PROCESSED_INLINING_CANDIDATE_ANY;

import com.android.tools.r8.FeatureSplit;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Definition;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramMethod;

public class FeatureSplitBoundaryOptimizationUtils {

  public static FeatureSplit getMergeKeyForHorizontalClassMerging(
      DexProgramClass clazz, AppView<?> appView) {
    return appView.appInfo().getClassToFeatureSplitMap().getFeatureSplit(clazz, appView);
  }

  public static boolean isSafeForAccess(
      Definition definition,
      ProgramDefinition context,
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    return !appView.options().hasFeatureSplitConfiguration()
        || !definition.isProgramDefinition()
        || isSafeForAccess(definition.asProgramDefinition(), context, appView);
  }

  private static boolean isSafeForAccess(
      ProgramDefinition definition,
      ProgramDefinition context,
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    assert appView.options().hasFeatureSplitConfiguration();
    ClassToFeatureSplitMap classToFeatureSplitMap = appView.appInfo().getClassToFeatureSplitMap();
    if (classToFeatureSplitMap.isInSameFeature(definition, context, appView)) {
      return true;
    }
    if (!classToFeatureSplitMap.isInBase(definition, appView)) {
      return false;
    }
    // If isolated splits are enabled then the resolved item must be either (1) public or (2)
    // a protected member and the access is in a subclass of the resolved member's holder.
    if (appView.options().getFeatureSplitConfiguration().isIsolatedSplitsEnabled()) {
      if (definition.isClass()) {
        if (!definition.getAccessFlags().isPublic()) {
          return false;
        }
      } else if (definition.getAccessFlags().isPackagePrivateOrProtected()) {
        if (definition.getAccessFlags().isPackagePrivate()
            || !appView
                .appInfo()
                .isSubtype(context.getContextClass(), definition.getContextClass())) {
          return false;
        }
      }
    }
    return true;
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
