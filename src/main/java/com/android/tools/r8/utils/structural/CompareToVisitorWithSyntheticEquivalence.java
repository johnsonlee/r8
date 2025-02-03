// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.structural;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.structural.StructuralItem.CompareToAccept;

public class CompareToVisitorWithSyntheticEquivalence extends CompareToVisitorBase {

  public static <T> int run(
      T item1,
      T item2,
      RepresentativeMap<DexType> typeMap,
      RepresentativeMap<DexMethod> methodMap,
      StructuralMapping<T> visit) {
    return run(item1, item2, typeMap, methodMap, (i1, i2, visitor) -> visitor.visit(i1, i2, visit));
  }

  public static <T> int run(
      T item1,
      T item2,
      RepresentativeMap<DexType> typeMap,
      RepresentativeMap<DexMethod> methodMap,
      CompareToAccept<T> compareToAccept) {
    if (item1 == item2) {
      return 0;
    }
    CompareToVisitorWithSyntheticEquivalence state =
        new CompareToVisitorWithSyntheticEquivalence(typeMap, methodMap);
    return compareToAccept.acceptCompareTo(item1, item2, state);
  }

  private final RepresentativeMap<DexType> typeRepresentatives;
  private final RepresentativeMap<DexMethod> methodRepresentatives;

  public CompareToVisitorWithSyntheticEquivalence(
      RepresentativeMap<DexType> typeRepresentatives,
      RepresentativeMap<DexMethod> methodRepresentatives) {
    this.typeRepresentatives = typeRepresentatives;
    this.methodRepresentatives = methodRepresentatives;
  }

  @Override
  public int visitDexType(DexType type1, DexType type2) {
    if (type1.isIdenticalTo(type2)) {
      return 0;
    }
    DexType repr1 = typeRepresentatives.getRepresentative(type1);
    DexType repr2 = typeRepresentatives.getRepresentative(type2);
    return debug(repr1.getDescriptor().acceptCompareTo(repr2.getDescriptor(), this));
  }

  @Override
  public int visitDexMethod(DexMethod method1, DexMethod method2) {
    if (method1.isIdenticalTo(method2)) {
      return 0;
    }
    DexMethod repr1 = methodRepresentatives.getRepresentative(method1);
    DexMethod repr2 = methodRepresentatives.getRepresentative(method2);
    return debug(visit(repr1, repr2, repr1.getStructuralMapping()));
  }
}
