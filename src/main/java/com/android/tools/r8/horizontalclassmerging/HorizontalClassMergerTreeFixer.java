// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.classmerging.ClassMergerSharedData;
import com.android.tools.r8.classmerging.ClassMergerTreeFixer;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.utils.collections.DexMethodSignatureBiMap;
import com.android.tools.r8.utils.timing.Timing;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * The tree fixer traverses all program classes and finds and fixes references to old classes which
 * have been remapped to new classes by the class merger. While doing so, all updated changes are
 * tracked in {@link HorizontalClassMergerTreeFixer#lensBuilder}.
 */
public class HorizontalClassMergerTreeFixer
    extends ClassMergerTreeFixer<
        HorizontalClassMergerGraphLens.Builder,
        HorizontalClassMergerGraphLens,
        HorizontallyMergedClasses> {

  public HorizontalClassMergerTreeFixer(
      AppView<?> appView,
      ClassMergerSharedData classMergerSharedData,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo,
      HorizontallyMergedClasses mergedClasses,
      HorizontalClassMergerGraphLens.Builder lensBuilder) {
    super(appView, classMergerSharedData, immediateSubtypingInfo, lensBuilder, mergedClasses);
  }

  /**
   * Lets assume the following initial classes, where the class B should be merged into A: <code>
   *   class A {
   *     public A(A a) { ... }
   *     public A(A a, int v) { ... }
   *     public A(B b) { ... }
   *     public A(B b, int v) { ... }
   *   }
   *
   *   class B {
   *     public B(A a) { ... }
   *     public B(B b) { ... }
   *   }
   * </code>
   *
   * <p>The {@link ClassMerger} merges the constructors {@code A.<init>(B)} and {@code B.<init>(B)}
   * into the constructor {@code A.<init>(B, int)} to prevent any collisions when merging the
   * constructor into A. The extra integer argument determines which class' constructor is called.
   * The SynthArg is used to prevent a collision with the existing {@code A.<init>(B, int)}
   * constructor. All constructors {@code A.<init>(A, ...)} generate a constructor {@code
   * A.<init>(A, int, SynthClass)} but are otherwise ignored. During ClassMerging the constructor
   * produces the following mappings in the graph lens builder:
   *
   * <ul>
   *   <li>{@code B.<init>(B) <--> A.<init>(B, int, SynthArg)}
   *   <li>{@code A.<init>(B) <--> A.<init>(B, int, SynthArg)} (This mapping is representative)
   *   <li>{@code A.constructor$B(B) ---> A.constructor$B(B)}
   *   <li>{@code B.<init>(B) <--- A.constructor$B(B)}
   * </ul>
   *
   * <p>Note: The identity mapping is needed so that the method is remapped in the forward direction
   * if there are changes in the tree fixer. Otherwise, methods are only remapped in directions they
   * are already mapped in.
   *
   * <p>During the fixup, all type references to B are changed into A. This causes a collision
   * between {@code A.<init>(A, int, SynthClass)} and {@code A.<init>(B, int, SynthClass)}. This
   * collision should be fixed by adding an extra argument to {@code A.<init>(B, int, SynthClass)}.
   * The TreeFixer generates the following mapping of renamed methods:
   *
   * <ul>
   *   <li>{@code A.<init>(B, int, SynthArg) <--> A.<init>(A, int, SynthArg, ExtraArg)}
   *   <li>{@code A.constructor$B(B) <--> A.constructor$B(A)}
   * </ul>
   *
   * <p>This rewrites the previous method mappings to:
   *
   * <ul>
   *   <li>{@code B.<init>(B) <--- A.constructor$B(A)}
   *   <li>{@code A.constructor$B(B) ---> A.constructor$B(A)}
   *   <li>{@code B.<init>(B) <--> A.<init>(A, int, SynthArg, ExtraArg)}
   *   <li>{@code A.<init>(B) <--> A.<init>(A, int, SynthArg, ExtraArg)} (including represents)
   * </ul>
   */
  @Override
  public HorizontalClassMergerGraphLens run(ExecutorService executorService, Timing timing)
      throws ExecutionException {
    return super.run(executorService, timing);
  }

  @Override
  protected void traverseProgramClassesDepthFirst(
      DexProgramClass clazz,
      Set<DexProgramClass> seen,
      DexMethodSignatureBiMap<DexMethodSignature> state) {
    assert seen.add(clazz);
    if (mergedClasses.isMergeSource(clazz.getType())) {
      assert !clazz.hasMethodsOrFields();
      return;
    }
    DexMethodSignatureBiMap<DexMethodSignature> newState = fixupProgramClass(clazz, state);
    for (DexProgramClass subclass : immediateSubtypingInfo.getSubclasses(clazz)) {
      traverseProgramClassesDepthFirst(subclass, seen, newState);
    }
    for (DexType sourceType : mergedClasses.getSourcesFor(clazz.getType())) {
      DexProgramClass sourceClass = appView.definitionFor(sourceType).asProgramClass();
      for (DexProgramClass subclass : immediateSubtypingInfo.getSubclasses(sourceClass)) {
        traverseProgramClassesDepthFirst(subclass, seen, newState);
      }
    }
  }
}
