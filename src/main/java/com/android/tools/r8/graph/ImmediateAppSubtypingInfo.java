// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.utils.TraversalContinuation;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

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
    return internalCreate(
        appView,
        classes,
        Function.identity(),
        immediateSubtypes -> new ImmediateAppSubtypingInfo(appView, immediateSubtypes));
  }

  public void forEachTransitiveProgramSubclass(
      DexClass clazz, Consumer<? super DexProgramClass> consumer) {
    forEachTransitiveProgramSubclass(clazz, consumer, Function.identity());
  }

  public Set<DexProgramClass> getTransitiveProgramSubclasses(DexClass clazz) {
    return getTransitiveProgramSubclasses(clazz, Function.identity());
  }

  public Set<DexProgramClass> getTransitiveProgramSubclassesMatching(
      DexClass clazz, Predicate<DexProgramClass> predicate) {
    return getTransitiveProgramSubclassesMatching(clazz, Function.identity(), predicate);
  }

  public <TB, TC> TraversalContinuation<TB, TC> traverseTransitiveSubclasses(
      DexClass clazz, Function<DexClass, TraversalContinuation<TB, TC>> fn) {
    return traverseTransitiveSubclasses(clazz, Function.identity(), fn);
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
