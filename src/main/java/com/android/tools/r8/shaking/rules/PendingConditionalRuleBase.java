// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.rules;

import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.shaking.Enqueuer;
import com.android.tools.r8.shaking.MinimumKeepInfoCollection;
import java.util.ArrayList;
import java.util.List;

public abstract class PendingConditionalRuleBase<T> {

  private final List<T> outstandingPreconditions;
  private final List<DexReference> satisfiedPreconditions;
  private final MinimumKeepInfoCollection consequences;

  PendingConditionalRuleBase(List<T> preconditions, MinimumKeepInfoCollection consequences) {
    this.outstandingPreconditions = preconditions;
    this.satisfiedPreconditions = new ArrayList<>(preconditions.size());
    this.consequences = consequences;
  }

  abstract DexReference getReference(T item);

  abstract boolean isSatisfied(T item, Enqueuer enqueuer);

  MinimumKeepInfoCollection getConsequences() {
    return consequences;
  }

  MaterializedConditionalRule asMaterialized() {
    assert !isOutstanding();
    return new MaterializedConditionalRule(satisfiedPreconditions, consequences);
  }

  final boolean isOutstanding() {
    return !outstandingPreconditions.isEmpty();
  }

  final boolean isSatisfiedAfterUpdate(Enqueuer enqueuer) {
    assert isOutstanding();
    int newlySatisfied = 0;
    for (T precondition : outstandingPreconditions) {
      if (isSatisfied(precondition, enqueuer)) {
        ++newlySatisfied;
        satisfiedPreconditions.add(getReference(precondition));
      }
    }
    // Not satisfied and nothing changed.
    if (newlySatisfied == 0) {
      return false;
    }
    // The rule is satisfied in full.
    if (newlySatisfied == outstandingPreconditions.size()) {
      outstandingPreconditions.clear();
      return true;
    }
    // Partially satisfied.
    // This is expected to be the uncommon case so the update to outstanding is delayed to here.
    outstandingPreconditions.removeIf(
        outstanding -> satisfiedPreconditions.contains(getReference(outstanding)));
    assert isOutstanding();
    return false;
  }
}
