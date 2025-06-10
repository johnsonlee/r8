// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.utils.timing.Timing;
import java.util.function.BiPredicate;

public interface MutableFieldAccessInfoCollection<
        S extends MutableFieldAccessInfoCollection<S, T>, T extends MutableFieldAccessInfo>
    extends FieldAccessInfoCollection<T> {

  void destroyAccessContexts();

  T extend(DexField field, FieldAccessInfoImpl info);

  void flattenAccessContexts();

  void removeIf(BiPredicate<DexField, FieldAccessInfoImpl> predicate);

  void restrictToProgram(DexDefinitionSupplier definitions);

  S rewrittenWithLens(DexDefinitionSupplier definitions, GraphLens lens, Timing timing);

  S withoutPrunedItems(PrunedItems prunedItems);
}
