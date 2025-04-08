// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.android.tools.r8.graph.DexApplication.classesWithDeterministicOrder;
import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public class ImmediateAppSubtypingInfo extends ImmediateSubtypingInfo<DexClass, DexClass> {

  private ImmediateAppSubtypingInfo(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Map<DexClass, List<DexClass>> immediateSubtypes) {
    super(appView, immediateSubtypes);
  }

  public static ImmediateAppSubtypingInfo create(
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    assert !appView.getSyntheticItems().hasPendingSyntheticClasses();
    DirectMappedDexApplication app = appView.app().asDirect();
    Iterable<DexClass> classes =
        Iterables.concat(app.programClasses(), app.classpathClasses(), app.libraryClasses());
    return create(appView, classes);
  }

  public static ImmediateAppSubtypingInfo create(
      AppView<? extends AppInfoWithClassHierarchy> appView, Iterable<DexClass> classes) {
    return internalCreate(
        appView,
        classes,
        Function.identity(),
        immediateSubtypes -> new ImmediateAppSubtypingInfo(appView, immediateSubtypes));
  }

  @Override
  DexClass toS(DexClass clazz) {
    return clazz;
  }

  // TODO(b/188395655): This is equivalent to just sorting all interfaces?
  public List<DexClass> computeReachableInterfacesWithDeterministicOrder() {
    List<DexClass> interfaces = new ArrayList<>();
    forEachInterface(interfaces::add);
    return classesWithDeterministicOrder(interfaces);
  }

  private void forEachInterface(Consumer<DexClass> consumer) {
    DexClass objectClass = appView.definitionFor(appView.dexItemFactory().objectType);
    forEachImmediateSubClassMatching(objectClass, DexClass::isInterface, consumer);
  }

  public void update(AppView<? extends AppInfoWithClassHierarchy> appView) {
    if (!appView.getSyntheticItems().hasPendingSyntheticClasses()) {
      return;
    }
    for (DexClass clazz : appView.getSyntheticItems().getAllPendingSyntheticClasses()) {
      for (DexType supertype : clazz.allImmediateSupertypes()) {
        DexClass superclass = appView.definitionFor(supertype);
        if (superclass == null) {
          continue;
        }
        immediateSubtypes.computeIfAbsent(superclass, ignoreKey(ArrayList::new)).add(clazz);
      }
    }
  }

  public boolean verifyUpToDate(AppView<AppInfoWithClassHierarchy> appView) {
    DirectMappedDexApplication app = appView.app().asDirect();
    Iterable<DexClass> classes =
        Iterables.concat(
            app.programClasses(),
            app.classpathClasses(),
            app.libraryClasses(),
            appView.getSyntheticItems().getAllPendingSyntheticClasses());
    for (DexClass clazz : classes) {
      assert verifyUpToDate(appView, clazz);
    }
    return true;
  }

  private boolean verifyUpToDate(AppView<AppInfoWithClassHierarchy> appView, DexClass clazz) {
    for (DexType supertype : clazz.allImmediateSupertypes()) {
      DexClass superclass = appView.definitionFor(supertype);
      if (superclass == null) {
        continue;
      }
      assert getSubclasses(superclass).contains(clazz)
              || (clazz.isLibraryClass()
                  && !appView.definitionFor(clazz.getType()).isLibraryClass())
          : "Expected subtypes(" + supertype.getTypeName() + ") to include " + clazz.getTypeName();
    }
    return true;
  }
}
