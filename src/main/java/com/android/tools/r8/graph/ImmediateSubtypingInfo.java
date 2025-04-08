// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.utils.MapUtils.ignoreKey;
import static com.google.common.base.Predicates.alwaysTrue;

import com.android.tools.r8.utils.TraversalContinuation;
import com.android.tools.r8.utils.WorkList;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public abstract class ImmediateSubtypingInfo<S extends DexClass, T extends DexClass> {

  final AppView<? extends AppInfoWithClassHierarchy> appView;
  final Map<S, List<T>> immediateSubtypes;

  ImmediateSubtypingInfo(
      AppView<? extends AppInfoWithClassHierarchy> appView, Map<S, List<T>> immediateSubtypes) {
    this.appView = appView;
    this.immediateSubtypes = immediateSubtypes;
  }

  static <S extends DexClass, T extends DexClass, U extends ImmediateSubtypingInfo<S, T>>
      U internalCreate(
          AppView<? extends AppInfoWithClassHierarchy> appView,
          Iterable<T> classes,
          Function<DexClass, S> cast,
          Function<Map<S, List<T>>, U> factory) {
    Map<S, List<T>> immediateSubtypes = new IdentityHashMap<>();
    for (T clazz : classes) {
      clazz.forEachImmediateSupertype(
          supertype -> {
            S superclass = cast.apply(appView.definitionFor(supertype));
            if (superclass != null) {
              immediateSubtypes.computeIfAbsent(superclass, ignoreKey(ArrayList::new)).add(clazz);
            }
          });
    }
    return factory.apply(immediateSubtypes);
  }

  public void forEachImmediateSuperClass(DexClass clazz, Consumer<? super DexClass> consumer) {
    forEachImmediateSuperClassMatching(
        clazz,
        (supertype, superclass) -> superclass != null,
        (supertype, superclass) -> consumer.accept(superclass));
  }

  public void forEachImmediateSuperClass(
      DexClass clazz, BiConsumer<? super DexType, ? super DexClass> consumer) {
    forEachImmediateSuperClassMatching(clazz, (supertype, superclass) -> true, consumer);
  }

  public void forEachImmediateSuperClassMatching(
      DexClass clazz,
      BiPredicate<? super DexType, ? super DexClass> predicate,
      BiConsumer<? super DexType, ? super DexClass> consumer) {
    clazz.forEachImmediateSupertype(
        supertype -> {
          DexClass superclass = appView.definitionFor(supertype);
          if (predicate.test(supertype, superclass)) {
            consumer.accept(supertype, superclass);
          }
        });
  }

  public void forEachImmediateSuperClassMatching(
      DexClass clazz, Predicate<? super DexClass> predicate, Consumer<? super DexClass> consumer) {
    clazz.forEachImmediateSupertype(
        supertype -> {
          DexClass superclass = appView.definitionFor(supertype);
          if (superclass != null && predicate.test(superclass)) {
            consumer.accept(superclass);
          }
        });
  }

  public void forEachImmediateProgramSuperClass(
      DexProgramClass clazz, Consumer<? super DexProgramClass> consumer) {
    forEachImmediateProgramSuperClassMatching(clazz, alwaysTrue(), consumer);
  }

  public void forEachImmediateProgramSuperClassMatching(
      DexProgramClass clazz,
      Predicate<? super DexProgramClass> predicate,
      Consumer<? super DexProgramClass> consumer) {
    clazz.forEachImmediateSupertype(
        supertype -> {
          DexProgramClass superclass = asProgramClassOrNull(appView.definitionFor(supertype));
          if (superclass != null && predicate.test(superclass)) {
            consumer.accept(superclass);
          }
        });
  }

  public void forEachImmediateSubClassMatching(
      S clazz, Predicate<? super T> predicate, Consumer<? super T> consumer) {
    getSubclasses(clazz)
        .forEach(
            subclass -> {
              if (predicate.test(subclass)) {
                consumer.accept(subclass);
              }
            });
  }

  void forEachTransitiveProgramSubclass(
      S clazz, Consumer<? super DexProgramClass> consumer, Function<DexClass, S> cast) {
    forEachTransitiveProgramSubclassMatching(clazz, consumer, cast, Predicates.alwaysTrue());
  }

  void forEachTransitiveProgramSubclassMatching(
      S clazz,
      Consumer<? super DexProgramClass> consumer,
      Function<DexClass, S> cast,
      Predicate<DexProgramClass> predicate) {
    WorkList<DexClass> worklist = WorkList.newIdentityWorkList(getSubclasses(clazz));
    worklist.process(
        subclass -> {
          if (subclass.isProgramClass()) {
            DexProgramClass programSubclass = subclass.asProgramClass();
            if (predicate.test(programSubclass)) {
              consumer.accept(programSubclass);
            }
          }
          S subclassOrNull = cast.apply(subclass);
          if (subclassOrNull != null) {
            worklist.addIfNotSeen(getSubclasses(subclassOrNull));
          }
        });
  }

  public List<T> getSubclasses(S clazz) {
    return immediateSubtypes.getOrDefault(clazz, Collections.emptyList());
  }

  public Iterable<T> getSubinterfaces(S clazz) {
    assert clazz.isInterface();
    return Iterables.filter(getSubclasses(clazz), DexClass::isInterface);
  }

  public Iterable<DexProgramClass> getSuperProgramInterfaces(
      DexProgramClass clazz, DexDefinitionSupplier definitions) {
    return Iterables.filter(
        Iterables.transform(
            clazz.getInterfaces(), i -> asProgramClassOrNull(definitions.definitionFor(i, clazz))),
        Objects::nonNull);
  }

  Set<DexProgramClass> getTransitiveProgramSubclasses(S clazz, Function<DexClass, S> cast) {
    return getTransitiveProgramSubclassesMatching(clazz, cast, Predicates.alwaysTrue());
  }

  Set<DexProgramClass> getTransitiveProgramSubclassesMatching(
      S clazz, Function<DexClass, S> cast, Predicate<DexProgramClass> predicate) {
    Set<DexProgramClass> classes = Sets.newIdentityHashSet();
    forEachTransitiveProgramSubclassMatching(clazz, classes::add, cast, predicate);
    return classes;
  }

  public boolean hasSubclasses(S clazz) {
    return !getSubclasses(clazz).isEmpty();
  }

  <TB, TC> TraversalContinuation<TB, TC> traverseTransitiveSubclasses(
      S clazz,
      Function<DexClass, S> cast,
      Function<? super DexClass, TraversalContinuation<TB, TC>> fn) {
    TraversalContinuation<TB, TC> traversalContinuation = TraversalContinuation.doContinue();
    WorkList<DexClass> worklist = WorkList.newIdentityWorkList(getSubclasses(clazz));
    while (worklist.hasNext()) {
      DexClass subclass = worklist.next();
      traversalContinuation = fn.apply(subclass);
      if (traversalContinuation.shouldBreak()) {
        return traversalContinuation;
      }
      S subclassOrNull = cast.apply(subclass);
      if (subclassOrNull != null) {
        worklist.addIfNotSeen(getSubclasses(subclassOrNull));
      }
    }
    return traversalContinuation;
  }
}
