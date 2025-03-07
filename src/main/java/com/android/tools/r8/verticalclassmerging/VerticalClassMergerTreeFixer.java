// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.classmerging.ClassMergerSharedData;
import com.android.tools.r8.classmerging.ClassMergerTreeFixer;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.collections.DexMethodSignatureBiMap;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.collect.Iterables;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

class VerticalClassMergerTreeFixer
    extends ClassMergerTreeFixer<
        VerticalClassMergerGraphLens.Builder,
        VerticalClassMergerGraphLens,
        VerticallyMergedClasses> {

  private final List<IncompleteVerticalClassMergerBridgeCode> synthesizedBridges;

  VerticalClassMergerTreeFixer(
      AppView<AppInfoWithLiveness> appView,
      ClassMergerSharedData classMergerSharedData,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo,
      VerticalClassMergerResult verticalClassMergerResult) {
    super(
        appView,
        classMergerSharedData,
        immediateSubtypingInfo,
        VerticalClassMergerGraphLens.Builder.createBuilderForFixup(verticalClassMergerResult),
        verticalClassMergerResult.getVerticallyMergedClasses());
    this.synthesizedBridges = verticalClassMergerResult.getSynthesizedBridges();
  }

  @Override
  public VerticalClassMergerGraphLens run(ExecutorService executorService, Timing timing)
      throws ExecutionException {
    VerticalClassMergerGraphLens lens = super.run(executorService, timing);
    for (IncompleteVerticalClassMergerBridgeCode synthesizedBridge : synthesizedBridges) {
      synthesizedBridge.updateMethodSignatures(lens);
    }
    return lens;
  }

  @Override
  public void preprocess() {
    appView
        .getKeepInfo()
        .forEachPinnedMethod(
            method -> {
              if (!method.isInstanceInitializer(dexItemFactory)) {
                keptSignatures.add(method);
              }
            },
            appView.options());
  }

  @Override
  public void postprocess() {
    lensBuilder.fixupContextualVirtualToDirectMethodMaps();
  }

  @Override
  protected boolean isRoot(DexProgramClass clazz) {
    if (!super.isRoot(clazz)) {
      return false;
    }
    return !Iterables.any(
        mergedClasses.getSourcesFor(clazz.getType()),
        source -> isRoot(asProgramClassOrNull(appView.definitionFor(source))));
  }

  @Override
  protected void traverseProgramClassesDepthFirst(
      DexProgramClass clazz,
      Set<DexProgramClass> seen,
      DexMethodSignatureBiMap<DexMethodSignature> state) {
    assert seen.add(clazz) : clazz.getTypeName();
    if (mergedClasses.isMergeSource(clazz.getType())) {
      assert !clazz.hasMethodsOrFields();
      DexProgramClass target =
          Iterables.getOnlyElement(immediateSubtypingInfo.getSubclasses(clazz));
      traverseProgramClassesDepthFirst(target, seen, state);
    } else {
      DexMethodSignatureBiMap<DexMethodSignature> newState = fixupProgramClass(clazz, state);
      for (DexProgramClass subclass : immediateSubtypingInfo.getSubclasses(clazz)) {
        traverseProgramClassesDepthFirst(subclass, seen, newState);
      }
    }
  }
}
