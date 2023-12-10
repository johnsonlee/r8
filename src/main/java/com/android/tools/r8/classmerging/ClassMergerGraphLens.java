// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.classmerging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.classmerging.MergedClasses;
import com.android.tools.r8.graph.lens.NestedGraphLens;
import com.android.tools.r8.ir.conversion.ExtraParameter;
import com.android.tools.r8.utils.collections.BidirectionalManyToManyRepresentativeMap;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneRepresentativeMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class ClassMergerGraphLens extends NestedGraphLens {

  public ClassMergerGraphLens(
      AppView<?> appView,
      BidirectionalManyToOneRepresentativeMap<DexField, DexField> fieldMap,
      Map<DexMethod, DexMethod> methodMap,
      BidirectionalManyToManyRepresentativeMap<DexType, DexType> typeMap,
      BidirectionalManyToManyRepresentativeMap<DexMethod, DexMethod> newMethodSignatures) {
    super(appView, fieldMap, methodMap, typeMap, newMethodSignatures);
  }

  public abstract static class BuilderBase<
      GL extends ClassMergerGraphLens, MC extends MergedClasses> {

    public abstract void addExtraParameters(
        DexMethod methodSignature, List<? extends ExtraParameter> extraParameters);

    public abstract void commitPendingUpdates();

    public abstract void fixupField(DexField from, DexField to);

    public abstract void fixupMethod(DexMethod from, DexMethod to);

    public abstract Set<DexMethod> getOriginalMethodReferences(DexMethod method);

    public abstract GL build(AppView<?> appView, MC mergedClasses);
  }
}
