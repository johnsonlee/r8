// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.outliner.bottomup;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.FeatureSplit;
import com.android.tools.r8.features.ClassToFeatureSplitMap;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.lightir.LirCode;
import com.google.common.base.Equivalence.Wrapper;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class OutlineCollection {

  private final AppView<?> appView;
  private final ClassToFeatureSplitMap classToFeatureSplitMap;

  private final Map<FeatureSplit, Map<Wrapper<LirCode<?>>, Outline>> outlines =
      new ConcurrentHashMap<>();

  OutlineCollection(AppView<?> appView) {
    this.appView = appView;
    this.classToFeatureSplitMap = appView.appInfo().getClassToFeatureSplitMap();
  }

  public Outline add(LirCode<?> lirCode, DexProto proto, ProgramMethod context) {
    // Get the outlines in the current feature.
    FeatureSplit feature = classToFeatureSplitMap.getFeatureSplit(context.getHolder(), appView);
    Map<Wrapper<LirCode<?>>, Outline> outlinesInFeature =
        outlines.computeIfAbsent(feature, ignoreKey(ConcurrentHashMap::new));
    // Add the outline.
    Wrapper<LirCode<?>> lirCodeWrapper = BottomUpOutlinerLirCodeEquivalence.get().wrap(lirCode);
    return outlinesInFeature.computeIfAbsent(lirCodeWrapper, w -> new Outline(w.get(), proto));
  }

  public Collection<Outline> getOutlines() {
    mergeOutlinesFromFeaturesIntoBase();
    return outlines.values().stream()
        .flatMap(x -> x.values().stream())
        .collect(Collectors.toList());
  }

  private void mergeOutlinesFromFeaturesIntoBase() {
    Map<Wrapper<LirCode<?>>, Outline> outlinesInBase = outlines.get(FeatureSplit.BASE);
    if (outlinesInBase == null) {
      return;
    }
    for (var entry : outlines.entrySet()) {
      FeatureSplit feature = entry.getKey();
      if (feature.isBase()) {
        continue;
      }
      Map<Wrapper<LirCode<?>>, Outline> outlinesInFeature = entry.getValue();
      var innerIterator = outlinesInFeature.entrySet().iterator();
      while (innerIterator.hasNext()) {
        var innerEntry = innerIterator.next();
        Wrapper<LirCode<?>> lirCodeWrapper = innerEntry.getKey();
        Outline outlineInBase = outlinesInBase.get(lirCodeWrapper);
        if (outlineInBase == null) {
          continue;
        }
        Outline outlineInFeature = innerEntry.getValue();
        outlineInBase.merge(outlineInFeature);
        innerIterator.remove();
      }
    }
  }
}
