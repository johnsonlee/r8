// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.rules;

import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.shaking.Enqueuer;
import com.android.tools.r8.shaking.MinimumKeepInfoCollection;
import java.util.List;

public class PendingConditionalRule extends PendingConditionalRuleBase<DexReference> {

  PendingConditionalRule(List<DexReference> preconditions, MinimumKeepInfoCollection consequences) {
    super(preconditions, consequences);
  }

  @Override
  DexReference getReference(DexReference item) {
    return item;
  }

  @Override
  boolean isSatisfied(DexReference item, Enqueuer enqueuer) {
    return enqueuer.isOriginalReferenceEffectivelyLive(item);
  }
}
