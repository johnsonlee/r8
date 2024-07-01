// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.startup;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.NestedGraphLens;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.utils.collections.BidirectionalOneToOneHashMap;
import com.android.tools.r8.utils.collections.BidirectionalOneToOneMap;
import com.android.tools.r8.utils.collections.MutableBidirectionalOneToOneMap;

public class NonStartupInStartupOutlinerLens extends NestedGraphLens {

  public NonStartupInStartupOutlinerLens(
      AppView<?> appView, BidirectionalOneToOneMap<DexMethod, DexMethod> methodMap) {
    super(appView, EMPTY_FIELD_MAP, methodMap, EMPTY_TYPE_MAP);
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public boolean isNonStartupInStartupOutlinerLens() {
    return true;
  }

  @Override
  protected InvokeType mapInvocationType(
      DexMethod newMethod, DexMethod newReboundMethod, DexMethod previousMethod, InvokeType type) {
    return newMethod.isIdenticalTo(previousMethod) ? type : InvokeType.STATIC;
  }

  public static class Builder {

    private final MutableBidirectionalOneToOneMap<DexMethod, DexMethod> newMethodSignatures =
        new BidirectionalOneToOneHashMap<>();

    public boolean isEmpty() {
      return newMethodSignatures.isEmpty();
    }

    public synchronized void recordMove(ProgramMethod from, ProgramMethod to) {
      newMethodSignatures.put(from.getReference(), to.getReference());
    }
    public NonStartupInStartupOutlinerLens build(
        AppView<? extends AppInfoWithClassHierarchy> appView) {
      return new NonStartupInStartupOutlinerLens(appView, newMethodSignatures);
    }
  }
}
