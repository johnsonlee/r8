// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.ArrayList;
import java.util.List;

public class VerticalClassMergerResult {

  private final VerticalClassMergerGraphLens.Builder lensBuilder;
  private final List<SynthesizedBridgeCode> synthesizedBridges;
  private final VerticallyMergedClasses verticallyMergedClasses;

  public VerticalClassMergerResult(
      VerticalClassMergerGraphLens.Builder lensBuilder,
      List<SynthesizedBridgeCode> synthesizedBridges,
      VerticallyMergedClasses verticallyMergedClasses) {
    this.lensBuilder = lensBuilder;
    this.synthesizedBridges = synthesizedBridges;
    this.verticallyMergedClasses = verticallyMergedClasses;
  }

  public static Builder builder(AppView<AppInfoWithLiveness> appView) {
    return new Builder(appView);
  }

  public static Builder builder(
      VerticalClassMergerGraphLens.Builder lensBuilder,
      List<SynthesizedBridgeCode> synthesizedBridges,
      VerticallyMergedClasses.Builder verticallyMergedClassesBuilder) {
    return new Builder(lensBuilder, synthesizedBridges, verticallyMergedClassesBuilder);
  }

  VerticalClassMergerGraphLens.Builder getLensBuilder() {
    return lensBuilder;
  }

  List<SynthesizedBridgeCode> getSynthesizedBridges() {
    return synthesizedBridges;
  }

  VerticallyMergedClasses getVerticallyMergedClasses() {
    return verticallyMergedClasses;
  }

  boolean isEmpty() {
    return verticallyMergedClasses.isEmpty();
  }

  public static class Builder {

    private final VerticalClassMergerGraphLens.Builder lensBuilder;
    private final List<SynthesizedBridgeCode> synthesizedBridges;
    private final VerticallyMergedClasses.Builder verticallyMergedClassesBuilder;

    Builder(AppView<AppInfoWithLiveness> appView) {
      this(
          new VerticalClassMergerGraphLens.Builder(),
          new ArrayList<>(),
          VerticallyMergedClasses.builder());
    }

    Builder(
        VerticalClassMergerGraphLens.Builder lensBuilder,
        List<SynthesizedBridgeCode> synthesizedBridges,
        VerticallyMergedClasses.Builder verticallyMergedClassesBuilder) {
      this.lensBuilder = lensBuilder;
      this.synthesizedBridges = synthesizedBridges;
      this.verticallyMergedClassesBuilder = verticallyMergedClassesBuilder;
    }

    synchronized void merge(VerticalClassMergerResult.Builder other) {
      lensBuilder.merge(other.lensBuilder);
      synthesizedBridges.addAll(other.synthesizedBridges);
      verticallyMergedClassesBuilder.merge(other.verticallyMergedClassesBuilder);
    }

    VerticalClassMergerResult build() {
      VerticallyMergedClasses verticallyMergedClasses = verticallyMergedClassesBuilder.build();
      return new VerticalClassMergerResult(
          lensBuilder, synthesizedBridges, verticallyMergedClasses);
    }
  }
}
