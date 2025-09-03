// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import static com.android.tools.r8.ir.analysis.type.ClassTypeElement.computeLeastUpperBoundOfInterfaces;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.LRUCacheTable;
import java.util.concurrent.ConcurrentHashMap;

public class TypeElementFactory {

  // ReferenceTypeElement canonicalization.
  private final ConcurrentHashMap<DexType, ReferenceTypeElement> referenceTypes =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<DexType, InterfaceCollection> classTypeInterfaces =
      new ConcurrentHashMap<>();
  public final LRUCacheTable<InterfaceCollection, InterfaceCollection, InterfaceCollection>
      leastUpperBoundOfInterfacesTable = LRUCacheTable.create(8, 8);

  public void clearTypeElementsCache() {
    referenceTypes.clear();
    classTypeInterfaces.clear();
    leastUpperBoundOfInterfacesTable.clear();
  }

  public boolean verifyNoCachedTypeElements() {
    assert referenceTypes.isEmpty();
    assert classTypeInterfaces.isEmpty();
    assert leastUpperBoundOfInterfacesTable.isEmpty();
    return true;
  }

  public ReferenceTypeElement createReferenceTypeElement(
      DexType type, Nullability nullability, AppView<?> appView) {
    // Class case:
    // If two concurrent threads will try to create the same class-type the concurrent hash map will
    // synchronize on the type in .computeIfAbsent and only a single class type is created.
    //
    // Array case:
    // Arrays will create a lattice element for its base type thus we take special care here.
    // Multiple threads may race recursively to create a base type. We have two cases:
    // (i)  If base type is class type and the threads will race to create the class type but only a
    //      single one will be created (Class case).
    // (ii) If base is ArrayLattice case we can use our induction hypothesis to get that only one
    //      element is created for us up to this case. Threads will now race to return from the
    //      latest recursive call and fight to get access to .computeIfAbsent to add the
    //      ArrayTypeElement but only one will enter. The property that only one
    //      ArrayTypeElement is created per level therefore holds inductively.
    TypeElement memberType = null;
    if (type.isArrayType()) {
      ReferenceTypeElement existing = referenceTypes.get(type);
      if (existing != null) {
        return existing.getOrCreateVariant(nullability);
      }
      memberType =
          TypeElement.fromDexType(
              type.toArrayElementType(appView.dexItemFactory()),
              Nullability.maybeNull(),
              appView,
              true);
    }
    TypeElement finalMemberType = memberType;
    return referenceTypes
        .computeIfAbsent(
            type,
            t -> {
              if (type.isClassType()) {
                if (!appView.enableWholeProgramOptimizations()) {
                  // Don't reason at the level of interfaces in D8.
                  return ClassTypeElement.createForD8(type, nullability);
                }
                assert appView.appInfo().hasClassHierarchy();
                if (appView.isInterface(type).isTrue()) {
                  return ClassTypeElement.create(
                      appView.dexItemFactory().objectType,
                      nullability,
                      appView.withClassHierarchy(),
                      InterfaceCollection.singleton(type));
                }
                // In theory, `interfaces` is the least upper bound of implemented interfaces.
                // It is expensive to walk through type hierarchy; collect implemented interfaces;
                // and compute the least upper bound of two interface sets. Hence, lazy
                // computations. Most likely during lattice join. See {@link
                // ClassTypeElement#getInterfaces}.
                return ClassTypeElement.create(type, nullability, appView.withClassHierarchy());
              }
              assert type.isArrayType();
              return ArrayTypeElement.create(finalMemberType, nullability);
            })
        .getOrCreateVariant(nullability);
  }

  public InterfaceCollection getLeastUpperBoundOfImplementedInterfacesOrDefault(
      DexType type, InterfaceCollection defaultValue) {
    return classTypeInterfaces.getOrDefault(type, defaultValue);
  }

  public InterfaceCollection getOrComputeLeastUpperBoundOfImplementedInterfaces(
      DexType type, AppView<? extends AppInfoWithClassHierarchy> appView) {
    return classTypeInterfaces.computeIfAbsent(
        type,
        t -> {
          InterfaceCollection itfs = appView.appInfo().implementedInterfaces(t);
          return computeLeastUpperBoundOfInterfaces(appView, itfs, itfs);
        });
  }
}
