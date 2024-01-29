// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.classmerging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.function.Supplier;

public class ClassMergerSharedData {

  // The collection of types that should be used to generate fresh constructor signatures.
  private final List<Supplier<DexType>> extraUnusedArgumentTypes;

  public ClassMergerSharedData(AppView<?> appView) {
    DexItemFactory factory = appView.dexItemFactory();
    extraUnusedArgumentTypes =
        ImmutableList.<Supplier<DexType>>builder()
            .add(Suppliers.ofInstance(factory.booleanType))
            .add(Suppliers.ofInstance(factory.byteType))
            .add(Suppliers.ofInstance(factory.charType))
            .add(Suppliers.ofInstance(factory.intType))
            .add(Suppliers.ofInstance(factory.shortType))
            .build();
  }

  public List<Supplier<DexType>> getExtraUnusedArgumentTypes() {
    return extraUnusedArgumentTypes;
  }
}
