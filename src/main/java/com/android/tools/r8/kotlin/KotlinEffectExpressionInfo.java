// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.KotlinMetadataUtils.rewriteIfNotNull;
import static com.android.tools.r8.kotlin.KotlinMetadataUtils.rewriteList;
import static com.android.tools.r8.utils.FunctionUtils.forEachApply;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.shaking.EnqueuerMetadataTraceable;
import com.android.tools.r8.utils.Reporter;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.function.Consumer;
import kotlin.metadata.KmConstantValue;
import kotlin.metadata.KmEffectExpression;

public class KotlinEffectExpressionInfo implements EnqueuerMetadataTraceable {

  private static final List<KotlinEffectExpressionInfo> NO_EXPRESSIONS = ImmutableList.of();
  private static final KotlinEffectExpressionInfo NO_EXPRESSION =
      new KotlinEffectExpressionInfo(
          new KmEffectExpression(), null, NO_EXPRESSIONS, NO_EXPRESSIONS);

  private final KmEffectExpression kmEffectExpression;
  private final KotlinTypeInfo isInstanceType;
  private final List<KotlinEffectExpressionInfo> andArguments;
  private final List<KotlinEffectExpressionInfo> orArguments;

  private KotlinEffectExpressionInfo(
      KmEffectExpression kmEffectExpression,
      KotlinTypeInfo isInstanceType,
      List<KotlinEffectExpressionInfo> andArguments,
      List<KotlinEffectExpressionInfo> orArguments) {
    this.kmEffectExpression = kmEffectExpression;
    this.isInstanceType = isInstanceType;
    this.andArguments = andArguments;
    this.orArguments = orArguments;
  }

  static KotlinEffectExpressionInfo create(
      KmEffectExpression kmEffectExpression, DexItemFactory factory, Reporter reporter) {
    if (kmEffectExpression == null) {
      return NO_EXPRESSION;
    }
    return new KotlinEffectExpressionInfo(
        kmEffectExpression,
        KotlinTypeInfo.create(kmEffectExpression.isInstanceType(), factory, reporter),
        create(kmEffectExpression.getAndArguments(), factory, reporter),
        create(kmEffectExpression.getOrArguments(), factory, reporter));
  }

  static List<KotlinEffectExpressionInfo> create(
      List<KmEffectExpression> effectExpressions, DexItemFactory factory, Reporter reporter) {
    if (effectExpressions.isEmpty()) {
      return NO_EXPRESSIONS;
    }
    ImmutableList.Builder<KotlinEffectExpressionInfo> builder = ImmutableList.builder();
    for (KmEffectExpression effectExpression : effectExpressions) {
      builder.add(create(effectExpression, factory, reporter));
    }
    return builder.build();
  }

  @Override
  public void trace(KotlinMetadataUseRegistry registry) {
    if (this == NO_EXPRESSION) {
      return;
    }
    if (isInstanceType != null) {
      isInstanceType.trace(registry);
    }
    forEachApply(andArguments, arg -> arg::trace, registry);
    forEachApply(orArguments, arg -> arg::trace, registry);
  }

  boolean rewrite(Consumer<KmEffectExpression> consumer, AppView<?> appView) {
    if (this == NO_EXPRESSION) {
      return false;
    }
    KmEffectExpression rewrittenKmEffectExpression = new KmEffectExpression();
    consumer.accept(rewrittenKmEffectExpression);
    KotlinFlagUtils.copyAllFlags(kmEffectExpression, rewrittenKmEffectExpression);
    rewrittenKmEffectExpression.setParameterIndex(kmEffectExpression.getParameterIndex());
    KmConstantValue constantValue = kmEffectExpression.getConstantValue();
    if (constantValue != null) {
      rewrittenKmEffectExpression.setConstantValue(constantValue);
    }
    boolean rewritten =
        rewriteIfNotNull(
            appView,
            isInstanceType,
            rewrittenKmEffectExpression::setInstanceType,
            KotlinTypeInfo::rewrite);
    rewritten |=
        rewriteList(
            appView,
            andArguments,
            rewrittenKmEffectExpression.getAndArguments(),
            KotlinEffectExpressionInfo::rewrite);
    rewritten |=
        rewriteList(
            appView,
            orArguments,
            rewrittenKmEffectExpression.getOrArguments(),
            KotlinEffectExpressionInfo::rewrite);
    return rewritten;
  }
}
