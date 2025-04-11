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
import java.util.List;
import java.util.function.Consumer;
import kotlin.metadata.KmTypeAlias;

// Holds information about KmTypeAlias
public class KotlinTypeAliasInfo implements EnqueuerMetadataTraceable {

  private final KmTypeAlias kmTypeAlias;
  private final KotlinTypeInfo underlyingType;
  private final KotlinTypeInfo expandedType;
  private final List<KotlinTypeParameterInfo> typeParameters;
  private final List<KotlinAnnotationInfo> annotations;

  private KotlinTypeAliasInfo(
      KmTypeAlias kmTypeAlias,
      KotlinTypeInfo underlyingType,
      KotlinTypeInfo expandedType,
      List<KotlinTypeParameterInfo> typeParameters,
      List<KotlinAnnotationInfo> annotations) {
    this.kmTypeAlias = kmTypeAlias;
    assert underlyingType != null;
    assert expandedType != null;
    this.underlyingType = underlyingType;
    this.expandedType = expandedType;
    this.typeParameters = typeParameters;
    this.annotations = annotations;
  }

  public static KotlinTypeAliasInfo create(
      KmTypeAlias alias, DexItemFactory factory, Reporter reporter) {
    return new KotlinTypeAliasInfo(
        alias,
        KotlinTypeInfo.create(alias.underlyingType, factory, reporter),
        KotlinTypeInfo.create(alias.expandedType, factory, reporter),
        KotlinTypeParameterInfo.create(alias.getTypeParameters(), factory, reporter),
        KotlinAnnotationInfo.create(alias.getAnnotations(), factory));
  }

  boolean rewrite(Consumer<KmTypeAlias> consumer, AppView<?> appView) {
    KmTypeAlias rewrittenKmTypeAlias = new KmTypeAlias(kmTypeAlias.getName());
    consumer.accept(rewrittenKmTypeAlias);
    KotlinFlagUtils.copyAllFlags(kmTypeAlias, rewrittenKmTypeAlias);
    boolean rewritten = underlyingType.rewrite(rewrittenKmTypeAlias::setUnderlyingType, appView);
    rewritten |= expandedType.rewrite(rewrittenKmTypeAlias::setExpandedType, appView);
    rewritten |=
        rewriteList(
            appView,
            typeParameters,
            rewrittenKmTypeAlias.getTypeParameters(),
            KotlinTypeParameterInfo::rewrite);
    rewritten |=
        rewriteList(
            appView,
            annotations,
            rewrittenKmTypeAlias.getAnnotations(),
            KotlinAnnotationInfo::rewrite);
    rewrittenKmTypeAlias.getVersionRequirements().addAll(kmTypeAlias.getVersionRequirements());
    return rewritten;
  }

  @Override
  public void trace(KotlinMetadataUseRegistry registry) {
    underlyingType.trace(registry);
    expandedType.trace(registry);
    forEachApply(typeParameters, typeParam -> typeParam::trace, registry);
    forEachApply(annotations, annotation -> annotation::trace, registry);
  }
}
