// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.MainDexInfo;
import com.android.tools.r8.utils.TraversalContinuation;
import com.android.tools.r8.verticalclassmerging.VerticalMergeGroup;
import com.google.common.collect.Iterables;

public class SafeConstructorInliningPolicy extends VerticalClassMergerPolicy {

  private final AppView<AppInfoWithLiveness> appView;
  private final MainDexInfo mainDexInfo;

  public SafeConstructorInliningPolicy(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
    this.mainDexInfo = appView.appInfo().getMainDexInfo();
  }

  @Override
  public boolean canMerge(VerticalMergeGroup group) {
    DexProgramClass sourceClass = group.getSource();
    DexProgramClass targetClass = group.getTarget();
    // If there is a constructor in the target, make sure that all source constructors can be
    // inlined.
    if (Iterables.isEmpty(targetClass.programInstanceInitializers())) {
      return true;
    }
    TraversalContinuation<?, ?> result =
        sourceClass.traverseProgramInstanceInitializers(
            method -> TraversalContinuation.breakIf(disallowInlining(method, targetClass)));
    return result.shouldContinue();
  }

  private boolean disallowInlining(ProgramMethod method, DexProgramClass context) {
    if (!appView.options().inlinerOptions().enableInlining) {
      return true;
    }
    Code code = method.getDefinition().getCode();
    if (code.isCfCode()) {
      CfCode cfCode = code.asCfCode();
      ConstraintWithTarget constraint =
          cfCode.computeInliningConstraint(appView, appView.graphLens(), method);
      if (constraint.isNever()) {
        return true;
      }
      // Constructors can have references beyond the root main dex classes. This can increase the
      // size of the main dex dependent classes and we should bail out.
      if (mainDexInfo.disallowInliningIntoContext(appView, context, method)) {
        return true;
      }
      return false;
    }
    if (code.isDefaultInstanceInitializerCode()) {
      return false;
    }
    return true;
  }

  @Override
  public String getName() {
    return "SafeConstructorInliningPolicy";
  }
}
