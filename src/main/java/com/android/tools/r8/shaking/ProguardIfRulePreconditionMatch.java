// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.RootSetUtils.ConsequentRootSetBuilder;
import com.android.tools.r8.utils.collections.ProgramMethodSet;

public class ProguardIfRulePreconditionMatch {

  private final ProguardIfRule ifRule;
  private final DexClass classMatch;
  private final ProgramMethodSet methodsMatch;

  public ProguardIfRulePreconditionMatch(ProguardIfRule ifRule, DexClass classMatch) {
    this(ifRule, classMatch, ProgramMethodSet.empty());
  }

  public ProguardIfRulePreconditionMatch(
      ProguardIfRule ifRule, DexClass classMatch, ProgramMethodSet methodsMatch) {
    this.ifRule = ifRule;
    this.classMatch = classMatch;
    this.methodsMatch = methodsMatch;
  }

  public ProguardIfRule getIfRuleWithPreconditionSet() {
    return ifRule.withPrecondition(classMatch);
  }

  public void disallowOptimizationsForReevaluation(
      Enqueuer enqueuer, ConsequentRootSetBuilder rootSetBuilder) {
    if (enqueuer.getMode().isInitialTreeShaking()
        && !ifRule.isTrivalAllClassMatch()
        && classMatch.isProgramClass()) {
      disallowClassOptimizationsForReevaluation(rootSetBuilder);
      disallowMethodOptimizationsForReevaluation(rootSetBuilder);
    }
  }

  private void disallowClassOptimizationsForReevaluation(ConsequentRootSetBuilder rootSetBuilder) {
    rootSetBuilder
        .getDependentMinimumKeepInfo()
        .getOrCreateUnconditionalMinimumKeepInfoFor(classMatch.getType())
        .asClassJoiner()
        .disallowClassInlining()
        .disallowHorizontalClassMerging()
        .disallowVerticalClassMerging();
  }

  private void disallowMethodOptimizationsForReevaluation(ConsequentRootSetBuilder rootSetBuilder) {
    for (ProgramMethod method : methodsMatch) {
      rootSetBuilder
          .getDependentMinimumKeepInfo()
          .getOrCreateUnconditionalMinimumKeepInfoFor(method.getReference())
          .asMethodJoiner()
          .disallowClassInlining()
          .disallowInlining();
    }
  }
}
