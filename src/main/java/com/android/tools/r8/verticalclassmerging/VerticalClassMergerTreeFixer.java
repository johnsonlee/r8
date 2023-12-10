// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging;

import com.android.tools.r8.classmerging.ClassMergerTreeFixer;
import com.android.tools.r8.classmerging.SyntheticArgumentClass;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.profile.rewriting.ProfileCollectionAdditions;
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

  private final List<SynthesizedBridgeCode> synthesizedBridges;

  VerticalClassMergerTreeFixer(
      AppView<AppInfoWithLiveness> appView,
      ProfileCollectionAdditions profileCollectionAdditions,
      SyntheticArgumentClass syntheticArgumentClass,
      VerticalClassMergerResult verticalClassMergerResult) {
    super(
        appView,
        VerticalClassMergerGraphLens.Builder.createBuilderForFixup(verticalClassMergerResult),
        verticalClassMergerResult.getVerticallyMergedClasses(),
        profileCollectionAdditions,
        syntheticArgumentClass);
    this.synthesizedBridges = verticalClassMergerResult.getSynthesizedBridges();
  }

  @Override
  public VerticalClassMergerGraphLens run(ExecutorService executorService, Timing timing)
      throws ExecutionException {
    VerticalClassMergerGraphLens lens = super.run(executorService, timing);
    for (SynthesizedBridgeCode synthesizedBridge : synthesizedBridges) {
      synthesizedBridge.updateMethodSignatures(lens);
    }
    return lens;
  }

  @Override
  public void postprocess() {
    lensBuilder.fixupContextualVirtualToDirectMethodMaps();
  }

  @Override
  public boolean isRunningBeforePrimaryOptimizationPass() {
    return true;
  }
}
