// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.rules;

import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.lens.NonIdentityGraphLens;
import com.android.tools.r8.shaking.MinimumKeepInfoCollection;
import com.android.tools.r8.utils.ListUtils;
import java.util.Collections;
import java.util.List;

public class MaterializedRules {

  private static final MaterializedRules EMPTY =
      new MaterializedRules(MinimumKeepInfoCollection.empty(), Collections.emptyList());

  private final MinimumKeepInfoCollection rootConsequences;
  private final List<MaterializedConditionalRule> conditionalRules;

  MaterializedRules(
      MinimumKeepInfoCollection rootConsequences,
      List<MaterializedConditionalRule> conditionalRules) {
    this.rootConsequences = rootConsequences;
    this.conditionalRules = conditionalRules;
  }

  public static MaterializedRules empty() {
    return EMPTY;
  }

  public MaterializedRules rewriteWithLens(NonIdentityGraphLens lens) {
    // The conditional rules are not rewritten. We assume witnessing instructions to carry the
    // original references to deem them effectively live.
    // TODO(b/323816623): Do we need to rewrite the consequent sets? Or would the constraints
    //  always ensure they remain if the keep info needs to be reapplied?
    return new MaterializedRules(rootConsequences.rewrittenWithLens(lens), conditionalRules);
  }

  public ApplicableRulesEvaluator toApplicableRules() {
    if (rootConsequences.isEmpty() && conditionalRules.isEmpty()) {
      return ApplicableRulesEvaluator.empty();
    }
    return new ApplicableRulesEvaluatorImpl<>(
        rootConsequences,
        ListUtils.map(conditionalRules, MaterializedConditionalRule::asPendingRule));
  }

  public void pruneItems(PrunedItems prunedItems) {
    rootConsequences.pruneItems(prunedItems);
    conditionalRules.removeIf(c -> c.pruneItems(prunedItems));
  }
}
