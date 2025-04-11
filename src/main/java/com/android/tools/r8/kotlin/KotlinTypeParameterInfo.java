// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.KotlinMetadataUtils.rewriteList;
import static com.android.tools.r8.utils.FunctionUtils.forEachApply;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.shaking.EnqueuerMetadataTraceable;
import com.android.tools.r8.utils.Reporter;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.function.Consumer;
import kotlin.metadata.KmType;
import kotlin.metadata.KmTypeParameter;
import kotlin.metadata.jvm.JvmExtensionsKt;

// Provides access to Kotlin information about a type-parameter.
public class KotlinTypeParameterInfo implements EnqueuerMetadataTraceable {

  private static final List<KotlinTypeParameterInfo> EMPTY_TYPE_PARAMETERS = ImmutableList.of();
  private static final List<KotlinTypeInfo> EMPTY_UPPER_BOUNDS = ImmutableList.of();

  private final KmTypeParameter kmTypeParameter;
  private final List<KotlinTypeInfo> originalUpperBounds;
  private final List<KotlinAnnotationInfo> annotations;

  private KotlinTypeParameterInfo(
      KmTypeParameter kmTypeParameter,
      List<KotlinTypeInfo> originalUpperBounds,
      List<KotlinAnnotationInfo> annotations) {
    this.kmTypeParameter = kmTypeParameter;
    this.originalUpperBounds = originalUpperBounds;
    this.annotations = annotations;
  }

  private static KotlinTypeParameterInfo create(
      KmTypeParameter kmTypeParameter, DexItemFactory factory, Reporter reporter) {
    return new KotlinTypeParameterInfo(
        kmTypeParameter,
        getUpperBounds(kmTypeParameter.getUpperBounds(), factory, reporter),
        KotlinAnnotationInfo.create(JvmExtensionsKt.getAnnotations(kmTypeParameter), factory));
  }

  static List<KotlinTypeParameterInfo> create(
      List<KmTypeParameter> kmTypeParameters, DexItemFactory factory, Reporter reporter) {
    if (kmTypeParameters.isEmpty()) {
      return EMPTY_TYPE_PARAMETERS;
    }
    ImmutableList.Builder<KotlinTypeParameterInfo> builder = ImmutableList.builder();
    for (KmTypeParameter kmTypeParameter : kmTypeParameters) {
      builder.add(create(kmTypeParameter, factory, reporter));
    }
    return builder.build();
  }

  private static List<KotlinTypeInfo> getUpperBounds(
      List<KmType> upperBounds, DexItemFactory factory, Reporter reporter) {
    if (upperBounds.isEmpty()) {
      return EMPTY_UPPER_BOUNDS;
    }
    ImmutableList.Builder<KotlinTypeInfo> builder = ImmutableList.builder();
    for (KmType upperBound : upperBounds) {
      builder.add(KotlinTypeInfo.create(upperBound, factory, reporter));
    }
    return builder.build();
  }

  boolean rewrite(Consumer<KmTypeParameter> consumer, AppView<?> appView) {
    KmTypeParameter rewrittenTypeParameter =
        new KmTypeParameter(
            kmTypeParameter.getName(), kmTypeParameter.getId(), kmTypeParameter.getVariance());
    consumer.accept(rewrittenTypeParameter);
    KotlinFlagUtils.copyAllFlags(kmTypeParameter, rewrittenTypeParameter);
    boolean rewritten =
        rewriteList(
            appView,
            originalUpperBounds,
            rewrittenTypeParameter.getUpperBounds(),
            KotlinTypeInfo::rewrite);
    rewritten |=
        rewriteList(
            appView,
            annotations,
            JvmExtensionsKt.getAnnotations(rewrittenTypeParameter),
            KotlinAnnotationInfo::rewrite);
    return rewritten;
  }

  @Override
  public void trace(KotlinMetadataUseRegistry registry) {
    forEachApply(originalUpperBounds, upperBound -> upperBound::trace, registry);
    forEachApply(annotations, annotation -> annotation::trace, registry);
  }
}
