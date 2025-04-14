// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.KotlinMetadataUtils.toJvmMethodSignature;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMember;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.shaking.EnqueuerMetadataTraceable;
import com.android.tools.r8.utils.Reporter;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import kotlin.metadata.KmLambda;
import kotlin.metadata.jvm.JvmExtensionsKt;
import kotlin.metadata.jvm.JvmMethodSignature;

// Holds information about a KmLambda
public class KotlinLambdaInfo implements EnqueuerMetadataTraceable {

  private final KotlinFunctionInfo function;
  private final boolean hasBacking;

  private KotlinLambdaInfo(KotlinFunctionInfo function, boolean hasBacking) {
    this.function = function;
    this.hasBacking = hasBacking;
  }

  static KotlinLambdaInfo create(
      DexClass clazz,
      KmLambda lambda,
      DexItemFactory factory,
      BiConsumer<DexEncodedMember<?, ?>, KotlinMemberLevelInfo> memberInfoConsumer,
      Reporter reporter) {
    if (lambda == null) {
      assert false;
      return null;
    }
    KotlinFunctionInfo kotlinFunctionInfo =
        KotlinFunctionInfo.create(lambda.function, factory, reporter);
    JvmMethodSignature signature = JvmExtensionsKt.getSignature(lambda.function);
    if (signature != null) {
      for (DexEncodedMethod method : clazz.methods()) {
        if (toJvmMethodSignature(method.getReference()).toString().equals(signature.toString())) {
          memberInfoConsumer.accept(method, kotlinFunctionInfo);
          return new KotlinLambdaInfo(kotlinFunctionInfo, true);
        }
      }
    }
    return new KotlinLambdaInfo(kotlinFunctionInfo, false);
  }

  boolean rewrite(Consumer<KmLambda> consumer, DexClass clazz, AppView<?> appView) {
    KmLambda kmLambda = new KmLambda();
    consumer.accept(kmLambda);
    if (!hasBacking) {
      return function.rewrite(kmLambda::setFunction, null, appView);
    }
    DexEncodedMethod backing = null;
    for (DexEncodedMethod method : clazz.methods()) {
      if (method.getKotlinInfo() == function) {
        backing = method;
        break;
      }
    }
    return function.rewrite(kmLambda::setFunction, backing, appView);
  }

  @Override
  public void trace(KotlinMetadataUseRegistry registry) {
    function.trace(registry);
  }
}
