// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.rules;

import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.shaking.Enqueuer;
import com.android.tools.r8.shaking.MinimumKeepInfoCollection;
import com.android.tools.r8.utils.ListUtils;
import java.util.List;

public class PendingInitialConditionalRule extends PendingConditionalRuleBase<ProgramDefinition> {

  PendingInitialConditionalRule(
      List<ProgramDefinition> preconditions, MinimumKeepInfoCollection consequences) {
    super(preconditions, consequences);
    assert !preconditions.isEmpty();
  }

  @Override
  List<DexReference> getReferences(List<ProgramDefinition> items) {
    return ListUtils.map(items, ProgramDefinition::getReference);
  }

  @Override
  boolean isSatisfied(ProgramDefinition item, Enqueuer enqueuer) {
    if (item.isClass()) {
      return enqueuer.isTypeLive(item.asClass());
    }
    if (item.isField()) {
      return enqueuer.isFieldLive(item.asField());
    }
    assert item.isMethod();
    return enqueuer.isMethodLive(item.asMethod());
  }
}
