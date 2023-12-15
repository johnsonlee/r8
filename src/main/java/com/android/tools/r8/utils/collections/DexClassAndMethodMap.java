// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.utils.DexClassAndMethodEquivalence;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class DexClassAndMethodMap<V> extends DexClassAndMemberMap<DexClassAndMethod, V> {

  private static final DexClassAndMethodMap<?> EMPTY = new DexClassAndMethodMap<>(ImmutableMap::of);

  private DexClassAndMethodMap(Supplier<Map<Wrapper<DexClassAndMethod>, V>> backingFactory) {
    super(backingFactory);
  }

  protected DexClassAndMethodMap(Map<Wrapper<DexClassAndMethod>, V> backing) {
    super(backing);
  }

  public static <V> DexClassAndMethodMap<V> create() {
    return new DexClassAndMethodMap<>(HashMap::new);
  }

  public static <V> DexClassAndMethodMap<V> create(int capacity) {
    return new DexClassAndMethodMap<>(new HashMap<>(capacity));
  }

  public static <V> DexClassAndMethodMap<V> createConcurrent() {
    return new DexClassAndMethodMap<>(ConcurrentHashMap::new);
  }

  @SuppressWarnings("unchecked")
  public static <V> DexClassAndMethodMap<V> empty() {
    return (DexClassAndMethodMap<V>) EMPTY;
  }

  @Override
  protected Wrapper<DexClassAndMethod> wrap(DexClassAndMethod method) {
    return DexClassAndMethodEquivalence.get().wrap(method);
  }
}
