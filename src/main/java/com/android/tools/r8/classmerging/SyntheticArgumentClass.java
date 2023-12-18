// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.horizontalclassmerging.HorizontalMergeGroup;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.synthesis.SyntheticItems.SyntheticKindSelector;
import com.google.common.base.Suppliers;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * Lets assume we are merging a class A that looks like:
 *
 * <pre>
 *   class A {
 *     A() { ... }
 *     A(int) { ... }
 *   }
 * </pre>
 *
 * If {@code A::<init>()} is merged with other constructors, then we need to prevent a conflict with
 * {@code A::<init>(int)}. To do this we introduce a synthetic class so that the new signature for
 * the merged constructor is {@code A::<init>(SyntheticClass, int)}, as this is guaranteed to be
 * unique.
 *
 * <p>This class generates a synthetic class in the package of the first class to be merged.
 */
public class SyntheticArgumentClass {

  private final List<Supplier<DexType>> syntheticClassTypes;

  private SyntheticArgumentClass(List<Supplier<DexType>> syntheticClassTypes) {
    this.syntheticClassTypes = syntheticClassTypes;
  }

  public List<Supplier<DexType>> getArgumentClasses() {
    return syntheticClassTypes;
  }

  public static class Builder {

    private final AppView<AppInfoWithLiveness> appView;

    public Builder(AppView<AppInfoWithLiveness> appView) {
      this.appView = appView;
    }

    private DexProgramClass synthesizeClass(
        DexProgramClass context, SyntheticKindSelector syntheticKindSelector) {
      return appView
          .getSyntheticItems()
          .createFixedClass(syntheticKindSelector, context, appView, builder -> {});
    }

    public SyntheticArgumentClass build(Collection<HorizontalMergeGroup> mergeGroups) {
      return build(getDeterministicContext(mergeGroups));
    }

    public SyntheticArgumentClass build(DexProgramClass deterministicContext) {
      List<Supplier<DexType>> syntheticArgumentTypes = new ArrayList<>();
      syntheticArgumentTypes.add(
          Suppliers.memoize(
              () ->
                  synthesizeClass(
                          deterministicContext, kinds -> kinds.HORIZONTAL_INIT_TYPE_ARGUMENT_1)
                      .getType()));
      syntheticArgumentTypes.add(
          Suppliers.memoize(
              () ->
                  synthesizeClass(
                          deterministicContext, kinds -> kinds.HORIZONTAL_INIT_TYPE_ARGUMENT_2)
                      .getType()));
      syntheticArgumentTypes.add(
          Suppliers.memoize(
              () ->
                  synthesizeClass(
                          deterministicContext, kinds -> kinds.HORIZONTAL_INIT_TYPE_ARGUMENT_3)
                      .getType()));
      return new SyntheticArgumentClass(syntheticArgumentTypes);
    }

    private static DexProgramClass getDeterministicContext(
        Collection<HorizontalMergeGroup> mergeGroups) {
      // Relies on the determinism of the merge groups.
      HorizontalMergeGroup mergeGroup = mergeGroups.iterator().next();
      assert mergeGroup.hasTarget();
      return mergeGroup.getTarget();
    }
  }
}
