// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ObjectAllocationInfoCollection;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.utils.TraversalContinuation;

public class ObjectAllocationInfoCollectionUtils {

  public static boolean mayHaveFinalizeMethodDirectlyOrIndirectly(
      AppView<AppInfoWithLiveness> appView, ClassTypeElement type) {
    return mayHaveFinalizeMethodDirectlyOrIndirectly(
        appView, type, appView.appInfo().getObjectAllocationInfoCollection());
  }

  @SuppressWarnings("ReferenceEquality")
  public static boolean mayHaveFinalizeMethodDirectlyOrIndirectly(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ClassTypeElement type,
      ObjectAllocationInfoCollection objectAllocationInfoCollection) {
    // Special case for java.lang.Object.
    if (type.getClassType() == appView.dexItemFactory().objectType
        && !type.getInterfaces().isEmpty()) {
      return type.getInterfaces()
          .anyMatch(
              (iface, isKnown) -> mayHaveFinalizer(appView, objectAllocationInfoCollection, iface));
    }
    return mayHaveFinalizeMethodDirectlyOrIndirectly(
        appView, type.getClassType(), objectAllocationInfoCollection);
  }

  @SuppressWarnings("ReferenceEquality")
  public static boolean mayHaveFinalizeMethodDirectlyOrIndirectly(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      DexType type,
      ObjectAllocationInfoCollection objectAllocationInfoCollection) {
    // Special case for java.lang.Object.
    if (type == appView.dexItemFactory().objectType) {
      // The type java.lang.Object could be any instantiated type. Assume a finalizer exists.
      return true;
    }
    return mayHaveFinalizer(appView, objectAllocationInfoCollection, type);
  }

  private static boolean mayHaveFinalizer(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ObjectAllocationInfoCollection objectAllocationInfoCollection,
      DexType type) {
    // A type may have an active finalizer if any derived instance has a finalizer.
    return objectAllocationInfoCollection
        .traverseInstantiatedSubtypes(
            type,
            clazz -> {
              if (objectAllocationInfoCollection.isInterfaceWithUnknownSubtypeHierarchy(clazz)) {
                return TraversalContinuation.doBreak();
              } else if (objectAllocationInfoCollection.hasInstantiatedStrictSubtype(clazz)) {
                // Only look for finalize() methods from the leaves of the instantiated hierarchy.
                return TraversalContinuation.doContinue();
              } else {
                return TraversalContinuation.breakIf(hasFinalizeMethodDirectly(appView, clazz));
              }
            },
            lambda -> {
              // Lambda classes do not have finalizers.
              return TraversalContinuation.doContinue();
            },
            appView.appInfo())
        .isBreak();
  }

  public static boolean hasFinalizeMethodDirectly(
      AppView<? extends AppInfoWithClassHierarchy> appView, DexClass clazz) {
    DexItemFactory factory = appView.dexItemFactory();
    while (clazz != null
        && clazz.getType().isNotIdenticalTo(factory.objectType)
        && clazz.getType().isNotIdenticalTo(factory.enumType)) {
      if (clazz.lookupMethod(factory.objectMembers.finalize) != null) {
        return true;
      }
      clazz = appView.definitionFor(clazz.getSuperType());
    }
    return false;
  }
}
