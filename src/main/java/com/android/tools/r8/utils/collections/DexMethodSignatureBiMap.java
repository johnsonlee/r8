// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.android.tools.r8.graph.DexMethodSignature;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableBiMap;

public class DexMethodSignatureBiMap<T> extends DexMethodSignatureMap<T> {

  public DexMethodSignatureBiMap() {
    this(HashBiMap.create());
  }

  public DexMethodSignatureBiMap(DexMethodSignatureBiMap<T> map) {
    this(HashBiMap.create(map));
  }

  public DexMethodSignatureBiMap(BiMap<DexMethodSignature, T> map) {
    super(map);
  }

  public static <T> DexMethodSignatureBiMap<T> empty() {
    return new DexMethodSignatureBiMap<>(ImmutableBiMap.of());
  }
}
