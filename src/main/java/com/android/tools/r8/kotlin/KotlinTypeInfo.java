// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.KotlinMetadataUtils.rewriteIfNotNull;
import static com.android.tools.r8.kotlin.KotlinMetadataUtils.rewriteList;
import static com.android.tools.r8.utils.FunctionUtils.forEachApply;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.shaking.EnqueuerMetadataTraceable;
import com.android.tools.r8.utils.Reporter;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.function.Consumer;
import kotlin.metadata.KmType;
import kotlin.metadata.KmTypeProjection;
import kotlin.metadata.jvm.JvmExtensionsKt;

// Provides access to Kotlin information about a kotlin type.
public class KotlinTypeInfo implements EnqueuerMetadataTraceable {

  private static final List<KotlinTypeProjectionInfo> EMPTY_ARGUMENTS = ImmutableList.of();

  private final KmType kmType;
  private final KotlinClassifierInfo classifier;
  private final KotlinTypeInfo abbreviatedType;
  private final KotlinTypeInfo outerType;
  private final List<KotlinTypeProjectionInfo> arguments;
  private final List<KotlinAnnotationInfo> annotations;
  private final KotlinFlexibleTypeUpperBoundInfo flexibleTypeUpperBound;
  private final boolean isRaw;

  KotlinTypeInfo(
      KmType kmType,
      KotlinClassifierInfo classifier,
      KotlinTypeInfo abbreviatedType,
      KotlinTypeInfo outerType,
      List<KotlinTypeProjectionInfo> arguments,
      List<KotlinAnnotationInfo> annotations,
      KotlinFlexibleTypeUpperBoundInfo flexibleTypeUpperBound,
      boolean isRaw) {
    this.kmType = kmType;
    this.classifier = classifier;
    this.abbreviatedType = abbreviatedType;
    this.outerType = outerType;
    this.arguments = arguments;
    this.annotations = annotations;
    this.flexibleTypeUpperBound = flexibleTypeUpperBound;
    this.isRaw = isRaw;
  }

  static KotlinTypeInfo create(KmType kmType, DexItemFactory factory, Reporter reporter) {
    if (kmType == null) {
      return null;
    }
    return new KotlinTypeInfo(
        kmType,
        KotlinClassifierInfo.create(kmType.classifier, factory, reporter),
        KotlinTypeInfo.create(kmType.getAbbreviatedType(), factory, reporter),
        KotlinTypeInfo.create(kmType.getOuterType(), factory, reporter),
        getArguments(kmType.getArguments(), factory, reporter),
        KotlinAnnotationInfo.create(JvmExtensionsKt.getAnnotations(kmType), factory),
        KotlinFlexibleTypeUpperBoundInfo.create(
            kmType.getFlexibleTypeUpperBound(), factory, reporter),
        JvmExtensionsKt.isRaw(kmType));
  }

  static List<KotlinTypeProjectionInfo> getArguments(
      List<KmTypeProjection> projections, DexItemFactory factory, Reporter reporter) {
    if (projections.isEmpty()) {
      return EMPTY_ARGUMENTS;
    }
    ImmutableList.Builder<KotlinTypeProjectionInfo> arguments = ImmutableList.builder();
    for (KmTypeProjection projection : projections) {
      arguments.add(KotlinTypeProjectionInfo.create(projection, factory, reporter));
    }
    return arguments.build();
  }

  boolean rewrite(Consumer<KmType> consumer, AppView<?> appView) {
    KmType rewrittenKmType = new KmType();
    consumer.accept(rewrittenKmType);
    KotlinFlagUtils.copyAllFlags(kmType, rewrittenKmType);
    boolean rewritten = classifier.rewrite(rewrittenKmType, appView);
    rewritten |=
        rewriteIfNotNull(
            appView, abbreviatedType, rewrittenKmType::setAbbreviatedType, KotlinTypeInfo::rewrite);
    rewritten |=
        rewriteIfNotNull(
            appView, outerType, rewrittenKmType::setOuterType, KotlinTypeInfo::rewrite);
    rewritten |=
        rewriteList(
            appView, arguments, rewrittenKmType.getArguments(), KotlinTypeProjectionInfo::rewrite);
    rewritten |=
        flexibleTypeUpperBound.rewrite(rewrittenKmType::setFlexibleTypeUpperBound, appView);
    if (annotations.isEmpty() && !isRaw) {
      return rewritten;
    }
    rewritten |=
        rewriteList(
            appView,
            annotations,
            JvmExtensionsKt.getAnnotations(rewrittenKmType),
            KotlinAnnotationInfo::rewrite);
    JvmExtensionsKt.setRaw(rewrittenKmType, isRaw);
    return rewritten;
  }

  @Override
  public void trace(KotlinMetadataUseRegistry registry) {
    classifier.trace(registry);
    if (abbreviatedType != null) {
      abbreviatedType.trace(registry);
    }
    if (outerType != null) {
      outerType.trace(registry);
    }
    forEachApply(arguments, argument -> argument::trace, registry);
    flexibleTypeUpperBound.trace(registry);
    forEachApply(annotations, annotation -> annotation::trace, registry);
  }

  public DexType rewriteType(GraphLens graphLens, GraphLens codeLens) {
    return classifier.rewriteType(graphLens, codeLens);
  }
}
