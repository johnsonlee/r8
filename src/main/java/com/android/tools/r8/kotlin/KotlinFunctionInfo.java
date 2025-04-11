// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.KotlinMetadataUtils.rewriteIfNotNull;
import static com.android.tools.r8.kotlin.KotlinMetadataUtils.rewriteList;
import static com.android.tools.r8.utils.FunctionUtils.forEachApply;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.Reporter;
import java.util.List;
import java.util.function.Consumer;
import kotlin.metadata.KmFunction;
import kotlin.metadata.jvm.JvmExtensionsKt;

// Holds information about KmFunction
public final class KotlinFunctionInfo implements KotlinMethodLevelInfo {
  // Original function.
  private final KmFunction kmFunction;
  // Information from original KmValueParameter(s) if available.
  private final List<KotlinValueParameterInfo> valueParameters;
  // Information from original KmFunction.returnType. Null if this is from a KmConstructor.
  public final KotlinTypeInfo returnType;
  // Information from original KmFunction.receiverType. Null if this is from a KmConstructor.
  private final KotlinTypeInfo receiverParameterType;
  // Information about original type parameters. Null if this is from a KmConstructor.
  private final List<KotlinTypeParameterInfo> typeParameters;
  // Information about the signature
  private final KotlinJvmMethodSignatureInfo signature;
  // Information about the lambdaClassOrigin.
  private final KotlinTypeReference lambdaClassOrigin;
  // Kotlin contract information.
  private final KotlinContractInfo contract;
  // A value describing if any of the parameters are crossinline.
  private final boolean crossInlineParameter;
  // Collection of context receiver types
  private final List<KotlinTypeInfo> contextReceiverTypes;

  private KotlinFunctionInfo(
      KmFunction kmFunction,
      KotlinTypeInfo returnType,
      KotlinTypeInfo receiverParameterType,
      List<KotlinValueParameterInfo> valueParameters,
      List<KotlinTypeParameterInfo> typeParameters,
      KotlinJvmMethodSignatureInfo signature,
      KotlinTypeReference lambdaClassOrigin,
      KotlinContractInfo contract,
      boolean crossInlineParameter,
      List<KotlinTypeInfo> contextReceiverTypes) {
    this.kmFunction = kmFunction;
    this.returnType = returnType;
    this.receiverParameterType = receiverParameterType;
    this.valueParameters = valueParameters;
    this.typeParameters = typeParameters;
    this.signature = signature;
    this.lambdaClassOrigin = lambdaClassOrigin;
    this.contract = contract;
    this.crossInlineParameter = crossInlineParameter;
    this.contextReceiverTypes = contextReceiverTypes;
  }

  public boolean hasCrossInlineParameter() {
    return crossInlineParameter;
  }

  static KotlinFunctionInfo create(
      KmFunction kmFunction, DexItemFactory factory, Reporter reporter) {
    boolean isCrossInline = false;
    List<KotlinValueParameterInfo> valueParameters =
        KotlinValueParameterInfo.create(kmFunction.getValueParameters(), factory, reporter);
    for (KotlinValueParameterInfo valueParameter : valueParameters) {
      if (valueParameter.isCrossInline()) {
        isCrossInline = true;
        break;
      }
    }
    return new KotlinFunctionInfo(
        kmFunction,
        KotlinTypeInfo.create(kmFunction.getReturnType(), factory, reporter),
        KotlinTypeInfo.create(kmFunction.getReceiverParameterType(), factory, reporter),
        valueParameters,
        KotlinTypeParameterInfo.create(kmFunction.getTypeParameters(), factory, reporter),
        KotlinJvmMethodSignatureInfo.create(JvmExtensionsKt.getSignature(kmFunction), factory),
        getlambdaClassOrigin(kmFunction, factory),
        KotlinContractInfo.create(kmFunction.getContract(), factory, reporter),
        isCrossInline,
        ListUtils.map(
            kmFunction.getContextReceiverTypes(),
            contextReceiverType -> KotlinTypeInfo.create(contextReceiverType, factory, reporter)));
  }

  private static KotlinTypeReference getlambdaClassOrigin(
      KmFunction kmFunction, DexItemFactory factory) {
    String lambdaClassOriginName = JvmExtensionsKt.getLambdaClassOriginName(kmFunction);
    if (lambdaClassOriginName != null) {
      return KotlinTypeReference.fromBinaryNameOrKotlinClassifier(
          lambdaClassOriginName, factory, lambdaClassOriginName);
    }
    return null;
  }

  public String getName() {
    return kmFunction.getName();
  }

  boolean rewriteNoBacking(Consumer<KmFunction> consumer, AppView<?> appView) {
    return rewrite(consumer, null, appView);
  }

  boolean rewrite(Consumer<KmFunction> consumer, DexEncodedMethod method, AppView<?> appView) {
    // TODO(b/154348683): Check method for flags to pass in.
    boolean rewritten = false;
    String finalName = getName();
    // Only rewrite the kotlin method name if it was equal to the method name when reading the
    // metadata.
    if (method != null) {
      String methodName = method.getReference().name.toString();
      String rewrittenName = appView.getNamingLens().lookupName(method.getReference()).toString();
      if (!methodName.equals(rewrittenName)) {
        rewritten = true;
        finalName = rewrittenName;
      }
    }
    KmFunction rewrittenKmFunction = new KmFunction(finalName);
    consumer.accept(rewrittenKmFunction);
    KotlinFlagUtils.copyAllFlags(kmFunction, rewrittenKmFunction);
    // TODO(b/154348149): ReturnType could have been merged to a subtype.
    rewritten |= returnType.rewrite(rewrittenKmFunction::setReturnType, appView);
    rewritten |=
        rewriteList(
            appView,
            valueParameters,
            rewrittenKmFunction.getValueParameters(),
            KotlinValueParameterInfo::rewrite);
    rewritten |=
        rewriteList(
            appView,
            typeParameters,
            rewrittenKmFunction.getTypeParameters(),
            KotlinTypeParameterInfo::rewrite);
    rewritten |=
        rewriteList(
            appView,
            contextReceiverTypes,
            rewrittenKmFunction.getContextReceiverTypes(),
            KotlinTypeInfo::rewrite);
    rewritten |=
        rewriteIfNotNull(
            appView,
            receiverParameterType,
            rewrittenKmFunction::setReceiverParameterType,
            KotlinTypeInfo::rewrite);
    rewrittenKmFunction.getVersionRequirements().addAll(kmFunction.getVersionRequirements());
    if (signature != null) {
      rewritten |=
          signature.rewrite(
              signature -> JvmExtensionsKt.setSignature(rewrittenKmFunction, signature),
              method,
              appView);
    }
    if (lambdaClassOrigin != null) {
      rewritten |=
          lambdaClassOrigin.toRenamedBinaryNameOrDefault(
              lambdaClassOriginName -> {
                if (lambdaClassOriginName != null) {
                  JvmExtensionsKt.setLambdaClassOriginName(
                      rewrittenKmFunction, lambdaClassOriginName);
                }
              },
              appView,
              null);
    }
    rewritten |= contract.rewrite(rewrittenKmFunction::setContract, appView);
    return rewritten;
  }

  @Override
  public boolean isFunction() {
    return true;
  }

  @Override
  public KotlinFunctionInfo asFunction() {
    return this;
  }

  public boolean isExtensionFunction() {
    return receiverParameterType != null;
  }

  @Override
  public void trace(KotlinMetadataUseRegistry registry) {
    forEachApply(valueParameters, param -> param::trace, registry);
    returnType.trace(registry);
    if (receiverParameterType != null) {
      receiverParameterType.trace(registry);
    }
    forEachApply(typeParameters, param -> param::trace, registry);
    forEachApply(contextReceiverTypes, type -> type::trace, registry);
    if (signature != null) {
      signature.trace(registry);
    }
    if (lambdaClassOrigin != null) {
      lambdaClassOrigin.trace(registry);
    }
    contract.trace(registry);
  }
}
