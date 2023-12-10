// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize;

import static com.android.tools.r8.graph.lens.NestedGraphLens.mapVirtualInterfaceInvocationTypes;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.lens.DefaultNonIdentityGraphLens;
import com.android.tools.r8.graph.lens.FieldLookupResult;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.MethodLookupResult;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

public class MemberRebindingLens extends DefaultNonIdentityGraphLens {

  private final Map<InvokeType, Map<DexMethod, DexMethod>> methodMaps;

  public MemberRebindingLens(
      AppView<AppInfoWithLiveness> appView, Map<InvokeType, Map<DexMethod, DexMethod>> methodMaps) {
    super(appView);
    this.methodMaps = methodMaps;
  }

  public static Builder builder(AppView<AppInfoWithLiveness> appView) {
    return new Builder(appView);
  }

  @Override
  public boolean isMemberRebindingLens() {
    return true;
  }

  @Override
  public MemberRebindingLens asMemberRebindingLens() {
    return this;
  }

  @Override
  protected FieldLookupResult internalDescribeLookupField(FieldLookupResult previous) {
    return previous;
  }

  @Override
  public MethodLookupResult internalDescribeLookupMethod(
      MethodLookupResult previous, DexMethod context, GraphLens codeLens) {
    Map<DexMethod, DexMethod> methodMap =
        methodMaps.getOrDefault(previous.getType(), Collections.emptyMap());
    DexMethod newReboundMethod =
        methodMap.getOrDefault(previous.getReference(), previous.getReference());
    return MethodLookupResult.builder(this, codeLens)
        .setReboundReference(newReboundMethod)
        .setReference(newReboundMethod)
        .setPrototypeChanges(previous.getPrototypeChanges())
        .setType(
            mapVirtualInterfaceInvocationTypes(
                appView, newReboundMethod, previous.getReference(), previous.getType()))
        .build();
  }

  @Override
  public boolean isIdentityLensForFields(GraphLens codeLens) {
    if (this == codeLens) {
      return true;
    }
    return getPrevious().isIdentityLensForFields(codeLens);
  }

  public static class Builder {

    private final AppView<AppInfoWithLiveness> appView;
    private final Map<InvokeType, Map<DexMethod, DexMethod>> methodMaps = new IdentityHashMap<>();

    private Builder(AppView<AppInfoWithLiveness> appView) {
      this.appView = appView;
    }

    @SuppressWarnings("ReferenceEquality")
    public void map(DexMethod from, DexMethod to, InvokeType type) {
      if (from == to) {
        assert !methodMaps.containsKey(type) || methodMaps.get(type).getOrDefault(from, to) == to;
        return;
      }
      Map<DexMethod, DexMethod> methodMap =
          methodMaps.computeIfAbsent(type, ignore -> new IdentityHashMap<>());
      assert methodMap.getOrDefault(from, to) == to;
      methodMap.put(from, to);
    }

    public MemberRebindingLens build() {
      return new MemberRebindingLens(appView, methodMaps);
    }
  }
}
