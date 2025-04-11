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
import kotlin.metadata.KmContract;
import kotlin.metadata.KmEffect;

public class KotlinContractInfo implements EnqueuerMetadataTraceable {

  private static final KotlinContractInfo NO_EFFECT = new KotlinContractInfo(ImmutableList.of());

  private final List<KotlinEffectInfo> effects;

  private KotlinContractInfo(List<KotlinEffectInfo> effects) {
    this.effects = effects;
  }

  static KotlinContractInfo create(
      KmContract kmContract, DexItemFactory factory, Reporter reporter) {
    if (kmContract == null) {
      return NO_EFFECT;
    }
    List<KmEffect> effects = kmContract.getEffects();
    if (effects.isEmpty()) {
      return NO_EFFECT;
    }
    ImmutableList.Builder<KotlinEffectInfo> builder = ImmutableList.builder();
    for (KmEffect effect : effects) {
      builder.add(KotlinEffectInfo.create(effect, factory, reporter));
    }
    return new KotlinContractInfo(builder.build());
  }

  @Override
  public void trace(KotlinMetadataUseRegistry registry) {
    forEachApply(effects, effect -> effect::trace, registry);
  }

  boolean rewrite(Consumer<KmContract> consumer, AppView<?> appView) {
    if (this == NO_EFFECT) {
      return false;
    }
    KmContract kmContract = new KmContract();
    consumer.accept(kmContract);
    return rewriteList(appView, effects, kmContract.getEffects(), KotlinEffectInfo::rewrite);
  }
}
