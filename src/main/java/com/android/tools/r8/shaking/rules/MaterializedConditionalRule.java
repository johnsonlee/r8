// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.rules;

import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.shaking.MinimumKeepInfoCollection;
import java.util.List;

public class MaterializedConditionalRule {

  private final List<DexReference> preconditions;
  private final MinimumKeepInfoCollection consequences;

  MaterializedConditionalRule(
      List<DexReference> preconditions, MinimumKeepInfoCollection consequences) {
    assert !preconditions.isEmpty();
    this.preconditions = preconditions;
    this.consequences = consequences;
  }

  PendingConditionalRule asPendingRule() {
    return new PendingConditionalRule(preconditions, consequences);
  }

  public boolean pruneItems(PrunedItems prunedItems) {
    // Preconditions cannot be pruned as they reference "original" program references which may be
    // in inlined positions even when the items themselves are "pruned".
    consequences.pruneItems(prunedItems);
    return consequences.isEmpty();
  }
}
