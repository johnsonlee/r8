// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.ir.analysis.type.ArrayTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.utils.AndroidApiLevelUtils;
import com.google.common.collect.Iterables;

public class DexTypeUtils {

  public static DexType computeApiSafeLeastUpperBound(
      AppView<? extends AppInfoWithClassHierarchy> appView, Iterable<DexType> types) {
    DexType leastUpperBound = computeLeastUpperBound(appView, types);
    return findApiSafeUpperBound(appView, leastUpperBound);
  }

  public static DexType computeLeastUpperBound(
      AppView<? extends AppInfoWithClassHierarchy> appView, Iterable<DexType> types) {
    TypeElement join =
        TypeElement.join(Iterables.transform(types, type -> type.toTypeElement(appView)), appView);
    return toDexType(appView, join);
  }

  public static boolean isApiSafe(
      AppView<? extends AppInfoWithClassHierarchy> appView, DexType type) {
    DexType apiSafeUpperBound = findApiSafeUpperBound(appView, type);
    return apiSafeUpperBound.isIdenticalTo(type);
  }

  public static boolean isLeastUpperBoundApiSafe(
      AppView<? extends AppInfoWithClassHierarchy> appView, Iterable<DexType> types) {
    return isApiSafe(appView, computeLeastUpperBound(appView, types));
  }

  public static DexType toDexType(
      AppView<? extends AppInfoWithClassHierarchy> appView, TypeElement type) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    if (type.isPrimitiveType()) {
      return type.asPrimitiveType().toDexType(dexItemFactory);
    }
    if (type.isArrayType()) {
      ArrayTypeElement arrayType = type.asArrayType();
      DexType baseType = toDexType(appView, arrayType.getBaseType());
      return baseType.toArrayType(arrayType.getNesting(), dexItemFactory);
    }
    assert type.isClassType();
    return type.asClassType().toDexType(dexItemFactory);
  }

  public static DexType findApiSafeUpperBound(
      AppView<? extends AppInfoWithClassHierarchy> appView, DexType type) {
    DexItemFactory factory = appView.dexItemFactory();
    DexType baseType = type.toBaseType(factory);
    if (baseType.isPrimitiveType()) {
      return type;
    }
    DexClass clazz = appView.definitionFor(baseType);
    if (clazz == null) {
      assert false : "We should not have found an upper bound if the hierarchy is missing";
      return type;
    }
    if (!clazz.isLibraryClass()
        || AndroidApiLevelUtils.isApiSafeForReference(clazz.asLibraryClass(), appView)
        || !clazz.hasSuperType()) {
      return type;
    }
    // Return the nearest API safe supertype.
    return findApiSafeUpperBound(appView, clazz.getSuperType());
  }

  public static boolean isTypeAccessibleInMethodContext(
      AppView<?> appView, DexType type, ProgramMethod context) {
    if (type.isPrimitiveType()) {
      return true;
    }
    if (type.isIdenticalTo(context.getHolderType())
        || (context.getHolder().hasSuperType()
            && type.isIdenticalTo(context.getHolder().getSuperType()))
        || context.getHolder().getInterfaces().contains(type)) {
      // In principle we don't know if the supertypes are guaranteed to be accessible in the current
      // context. However, if they aren't, the current class will never be successfully loaded
      // anyway.
      return true;
    }
    DexClass clazz = appView.definitionFor(type, context);
    if (clazz == null) {
      return false;
    }
    if (clazz.isLibraryClass()) {
      return AndroidApiLevelUtils.isApiSafeForReference(clazz.asLibraryClass(), appView);
    }
    if (appView.hasClassHierarchy()) {
      return AccessControl.isClassAccessible(clazz, context, appView.withClassHierarchy()).isTrue();
    }
    return false;
  }
}
