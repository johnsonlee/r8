// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexType;
import java.util.Map;

abstract class FieldNamingStateBase<T> {

  final AppView<? extends AppInfoWithClassHierarchy> appView;
  final Map<DexType, T> internalStates;

  FieldNamingStateBase(
      AppView<? extends AppInfoWithClassHierarchy> appView, Map<DexType, T> internalStates) {
    this.appView = appView;
    this.internalStates = internalStates;
  }

  final T getInternalState(DexType type) {
    DexType internalStateKey = getInternalStateKey(type);
    return internalStates.get(internalStateKey);
  }

  final T getOrCreateInternalState(DexField field) {
    return getOrCreateInternalState(field.type);
  }

  final T getOrCreateInternalState(DexType type) {
    DexType internalStateKey = getInternalStateKey(type);
    return internalStates.computeIfAbsent(internalStateKey, key -> createInternalState());
  }

  @SuppressWarnings("UnusedVariable")
  private DexType getInternalStateKey(DexType type) {
    // Returning the given type instead of void will implement aggressive overloading.
    return appView.dexItemFactory().voidType;
  }

  abstract T createInternalState();
}
