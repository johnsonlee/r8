// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.assistant.postprocessing.model;

import com.android.tools.r8.assistant.runtime.ReflectiveEventType;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.KeepInfoCollectionExported;

public class ServiceLoaderLoad extends ReflectiveEvent {

  private final DexType type;
  private final DexType classLoader;

  public ServiceLoaderLoad(
      ReflectiveEventType eventType, String[] stack, String[] args, DexItemFactory factory) {
    super(eventType, stack);
    assert args.length == 2;
    type = toType(args[0], factory);
    classLoader = toTypeOrTripleStar(args[0], factory);
  }

  @Override
  public boolean isServiceLoaderLoad() {
    return true;
  }

  @Override
  public ServiceLoaderLoad asServiceLoaderLoad() {
    return this;
  }

  public DexType getType() {
    return type;
  }

  @Override
  public String getContentsString() {
    return type + ", " + classLoader;
  }

  @Override
  public boolean isKeptBy(KeepInfoCollectionExported keepInfoCollectionExported) {
    return keepInfoCollectionExported.getKeepClassInfo(type.asTypeReference()) != null;
  }
}
