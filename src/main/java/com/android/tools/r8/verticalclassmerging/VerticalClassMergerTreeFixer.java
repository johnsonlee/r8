// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging;

import com.android.tools.r8.classmerging.ClassMergerSharedData;
import com.android.tools.r8.classmerging.ClassMergerTreeFixer;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Timing;
import java.util.List;
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
      VerticalClassMergerResult verticalClassMergerResult) {
    super(
        appView,
        classMergerSharedData,
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
}
