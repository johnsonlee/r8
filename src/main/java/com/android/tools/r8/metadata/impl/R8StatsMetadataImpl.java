// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.metadata.impl;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.keepanno.annotations.AnnotationPattern;
import com.android.tools.r8.keepanno.annotations.FieldAccessFlags;
import com.android.tools.r8.keepanno.annotations.KeepConstraint;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.UsedByReflection;
import com.android.tools.r8.metadata.R8StatsMetadata;
import com.android.tools.r8.shaking.KeepInfo;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

@UsedByReflection(
    description = "Keep and preserve @SerializedName for correct (de)serialization",
    constraints = {KeepConstraint.LOOKUP},
    constrainAnnotations = @AnnotationPattern(constant = SerializedName.class),
    kind = KeepItemKind.CLASS_AND_FIELDS,
    fieldAccess = {FieldAccessFlags.PRIVATE},
    fieldAnnotatedByClassConstant = SerializedName.class)
public class R8StatsMetadataImpl implements R8StatsMetadata {

  @Expose
  @SerializedName("noObfuscationPercentage")
  private final float noObfuscationPercentage;

  @Expose
  @SerializedName("noOptimizationPercentage")
  private final float noOptimizationPercentage;

  @Expose
  @SerializedName("noShrinkingPercentage")
  private final float noShrinkingPercentage;

  private R8StatsMetadataImpl(
      float noObfuscationPercentage, float noOptimizationPercentage, float noShrinkingPercentage) {
    this.noObfuscationPercentage = noObfuscationPercentage;
    this.noOptimizationPercentage = noOptimizationPercentage;
    this.noShrinkingPercentage = noShrinkingPercentage;
  }

  public static R8StatsMetadataImpl create(AppView<? extends AppInfoWithClassHierarchy> appView) {
    Counters counters = Counters.create(appView);
    return new R8StatsMetadataImpl(
        counters.getNoObfuscationPercentage(),
        counters.getNoOptimizationPercentage(),
        counters.getNoShrinkingPercentage());
  }

  @Override
  public float getNoObfuscationPercentage() {
    return noObfuscationPercentage;
  }

  @Override
  public float getNoOptimizationPercentage() {
    return noOptimizationPercentage;
  }

  @Override
  public float getNoShrinkingPercentage() {
    return noShrinkingPercentage;
  }

  private static class Counters {

    private int itemsCount = 0;
    private int noObfuscationCount = 0;
    private int noOptimizationCount = 0;
    private int noShrinkingCount = 0;

    private Counters() {}

    static Counters create(AppView<? extends AppInfoWithClassHierarchy> appView) {
      Counters counters = new Counters();
      for (DexProgramClass clazz : appView.appInfo().classes()) {
        counters.add(appView, clazz);
        clazz.forEachProgramMember(member -> counters.add(appView, member));
      }
      return counters;
    }

    private void add(
        AppView<? extends AppInfoWithClassHierarchy> appView, ProgramDefinition definition) {
      KeepInfo<?, ?> keepInfo = appView.getKeepInfo(definition);
      InternalOptions options = appView.options();
      itemsCount++;
      noObfuscationCount += BooleanUtils.intValue(!keepInfo.isMinificationAllowed(options));
      noOptimizationCount += BooleanUtils.intValue(!keepInfo.isOptimizationAllowed(options));
      noShrinkingCount += BooleanUtils.intValue(!keepInfo.isShrinkingAllowed(options));
    }

    float getNoObfuscationPercentage() {
      return toPercentageWithTwoDecimals(noObfuscationCount);
    }

    float getNoOptimizationPercentage() {
      return toPercentageWithTwoDecimals(noOptimizationCount);
    }

    float getNoShrinkingPercentage() {
      return toPercentageWithTwoDecimals(noShrinkingCount);
    }

    float toPercentageWithTwoDecimals(int count) {
      // Multiply by 100 twice to get percentage with two decimals.
      float number = (float) (count * 100 * 100) / itemsCount;
      return (float) Math.round(number) / 100;
    }
  }
}
