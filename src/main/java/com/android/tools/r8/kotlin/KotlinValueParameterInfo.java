// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.KotlinMetadataUtils.rewriteIfNotNull;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.shaking.EnqueuerMetadataTraceable;
import com.android.tools.r8.utils.Reporter;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.function.Consumer;
import kotlin.metadata.Attributes;
import kotlin.metadata.KmType;
import kotlin.metadata.KmValueParameter;

// Provides access to Kotlin information about value parameter.
class KotlinValueParameterInfo implements EnqueuerMetadataTraceable {
  private static final List<KotlinValueParameterInfo> EMPTY_VALUE_PARAMETERS = ImmutableList.of();
  // Original parameter.
  final KmValueParameter kmValueParameter;
  // Original information about the type.
  final KotlinTypeInfo type;
  // Indicates whether the formal parameter is originally `vararg`.
  final KotlinTypeInfo varargElementType;

  private KotlinValueParameterInfo(
      KmValueParameter kmValueParameter, KotlinTypeInfo type, KotlinTypeInfo varargElementType) {
    this.kmValueParameter = kmValueParameter;
    this.type = type;
    this.varargElementType = varargElementType;
  }

  boolean isCrossInline() {
    return Attributes.isCrossinline(kmValueParameter);
  }

  static KotlinValueParameterInfo create(
      KmValueParameter kmValueParameter, DexItemFactory factory, Reporter reporter) {
    if (kmValueParameter == null) {
      return null;
    }
    KmType kmType = kmValueParameter.getType();
    return new KotlinValueParameterInfo(
        kmValueParameter,
        KotlinTypeInfo.create(kmType, factory, reporter),
        KotlinTypeInfo.create(kmValueParameter.getVarargElementType(), factory, reporter));
  }

  static List<KotlinValueParameterInfo> create(
      List<KmValueParameter> parameters, DexItemFactory factory, Reporter reporter) {
    if (parameters.isEmpty()) {
      return EMPTY_VALUE_PARAMETERS;
    }
    ImmutableList.Builder<KotlinValueParameterInfo> builder = ImmutableList.builder();
    for (KmValueParameter parameter : parameters) {
      builder.add(create(parameter, factory, reporter));
    }
    return builder.build();
  }

  boolean rewrite(Consumer<KmValueParameter> consumer, AppView<?> appView) {
    KmValueParameter rewrittenKmValueParameter = new KmValueParameter(kmValueParameter.getName());
    consumer.accept(rewrittenKmValueParameter);
    KotlinFlagUtils.copyAllFlags(kmValueParameter, rewrittenKmValueParameter);
    boolean rewritten = type.rewrite(rewrittenKmValueParameter::setType, appView);
    rewritten |=
        rewriteIfNotNull(
            appView,
            varargElementType,
            rewrittenKmValueParameter::setVarargElementType,
            KotlinTypeInfo::rewrite);
    return rewritten;
  }

  @Override
  public void trace(KotlinMetadataUseRegistry registry) {
    type.trace(registry);
    if (varargElementType != null) {
      varargElementType.trace(registry);
    }
  }
}
