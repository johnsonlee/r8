// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.assistant.postprocessing.model;

import com.android.tools.r8.assistant.runtime.ReflectiveEventType;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.KeepInfoCollectionExported;
import com.android.tools.r8.utils.ArrayUtils;
import java.util.Arrays;

public class ProxyNewProxyInstance extends ReflectiveEvent {

  private final DexType classLoader;
  private final DexType[] interfaces;
  private final String invocationHandler;

  public ProxyNewProxyInstance(
      ReflectiveEventType eventType, String[] stack, String[] args, DexItemFactory factory) {
    super(eventType, stack);
    assert args.length > 2;
    classLoader = toTypeOrTripleStar(args[0], factory);
    invocationHandler = args[1];
    interfaces = new DexType[args.length - 2];
    for (int i = 2; i < args.length; i++) {
      interfaces[i - 2] = toType(args[i], factory);
    }
  }

  public DexType[] getInterfaces() {
    return interfaces;
  }

  @Override
  public boolean isProxyNewProxyInstance() {
    return true;
  }

  @Override
  public ProxyNewProxyInstance asProxyNewProxyInstance() {
    return this;
  }

  @Override
  public String getContentsString() {
    return invocationHandler + ", " + classLoader + ", " + Arrays.toString(interfaces);
  }

  @Override
  public boolean isKeptBy(KeepInfoCollectionExported keepInfoCollectionExported) {
    return ArrayUtils.none(
        interfaces,
        itf -> keepInfoCollectionExported.getKeepClassInfo(itf.asTypeReference()) == null);
  }
}
