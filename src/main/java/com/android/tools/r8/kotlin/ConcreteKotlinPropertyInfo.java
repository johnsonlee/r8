// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.KotlinMetadataUtils.rewriteIfNotNull;
import static com.android.tools.r8.kotlin.KotlinMetadataUtils.rewriteList;
import static com.android.tools.r8.utils.FunctionUtils.forEachApply;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.Reporter;
import java.util.List;
import java.util.function.Consumer;
import kotlin.metadata.KmProperty;
import kotlin.metadata.KmPropertyAccessorAttributes;
import kotlin.metadata.jvm.JvmExtensionsKt;

// Holds information about KmProperty
public class ConcreteKotlinPropertyInfo implements KotlinPropertyInfo {

  // Original property.
  private final KmProperty kmProperty;

  // Original return type information. This should never be NULL (even for setters without field).
  private final KotlinTypeInfo returnType;

  private final KotlinTypeInfo receiverParameterType;

  private final KotlinValueParameterInfo setterParameter;

  private final List<KotlinTypeParameterInfo> typeParameters;

  private final KotlinJvmFieldSignatureInfo fieldSignature;

  private final KotlinJvmMethodSignatureInfo getterSignature;

  private final KotlinJvmMethodSignatureInfo setterSignature;

  private final KotlinJvmMethodSignatureInfo syntheticMethodForAnnotations;

  private final KotlinJvmMethodSignatureInfo syntheticMethodForDelegate;
  // Collection of context receiver types
  private final List<KotlinTypeInfo> contextReceiverTypes;

  private ConcreteKotlinPropertyInfo(
      KmProperty kmProperty,
      KotlinTypeInfo returnType,
      KotlinTypeInfo receiverParameterType,
      KotlinValueParameterInfo setterParameter,
      List<KotlinTypeParameterInfo> typeParameters,
      KotlinJvmFieldSignatureInfo fieldSignature,
      KotlinJvmMethodSignatureInfo getterSignature,
      KotlinJvmMethodSignatureInfo setterSignature,
      KotlinJvmMethodSignatureInfo syntheticMethodForAnnotations,
      KotlinJvmMethodSignatureInfo syntheticMethodForDelegate,
      List<KotlinTypeInfo> contextReceiverTypes) {
    assert returnType != null;
    this.kmProperty = kmProperty;
    this.returnType = returnType;
    this.receiverParameterType = receiverParameterType;
    this.setterParameter = setterParameter;
    this.typeParameters = typeParameters;
    this.fieldSignature = fieldSignature;
    this.getterSignature = getterSignature;
    this.setterSignature = setterSignature;
    this.syntheticMethodForAnnotations = syntheticMethodForAnnotations;
    this.syntheticMethodForDelegate = syntheticMethodForDelegate;
    this.contextReceiverTypes = contextReceiverTypes;
  }

  public static ConcreteKotlinPropertyInfo create(
      KmProperty kmProperty, DexItemFactory factory, Reporter reporter) {
    return new ConcreteKotlinPropertyInfo(
        kmProperty,
        KotlinTypeInfo.create(kmProperty.getReturnType(), factory, reporter),
        KotlinTypeInfo.create(kmProperty.getReceiverParameterType(), factory, reporter),
        KotlinValueParameterInfo.create(kmProperty.getSetterParameter(), factory, reporter),
        KotlinTypeParameterInfo.create(kmProperty.getTypeParameters(), factory, reporter),
        KotlinJvmFieldSignatureInfo.create(JvmExtensionsKt.getFieldSignature(kmProperty), factory),
        KotlinJvmMethodSignatureInfo.create(
            JvmExtensionsKt.getGetterSignature(kmProperty), factory),
        KotlinJvmMethodSignatureInfo.create(
            JvmExtensionsKt.getSetterSignature(kmProperty), factory),
        KotlinJvmMethodSignatureInfo.create(
            JvmExtensionsKt.getSyntheticMethodForAnnotations(kmProperty), factory),
        KotlinJvmMethodSignatureInfo.create(
            JvmExtensionsKt.getSyntheticMethodForDelegate(kmProperty), factory),
        ListUtils.map(
            kmProperty.getContextReceiverTypes(),
            contextRecieverType -> KotlinTypeInfo.create(contextRecieverType, factory, reporter)));
  }

  @Override
  public KotlinJvmFieldSignatureInfo getFieldSignature() {
    return fieldSignature;
  }

  @Override
  public KotlinJvmMethodSignatureInfo getGetterSignature() {
    return getterSignature;
  }

  @Override
  public KotlinJvmMethodSignatureInfo getSetterSignature() {
    return setterSignature;
  }

  @Override
  public boolean rewriteNoBacking(Consumer<KmProperty> consumer, AppView<?> appView) {
    return rewrite(consumer, null, null, null, null, appView);
  }

  @Override
  public boolean rewrite(
      Consumer<KmProperty> consumer,
      DexEncodedField field,
      DexEncodedMethod getter,
      DexEncodedMethod setter,
      DexEncodedMethod syntheticMethodForAnnotationsMethod,
      AppView<?> appView) {
    KmProperty rewrittenKmProperty = new KmProperty(kmProperty.getName());
    consumer.accept(rewrittenKmProperty);
    KotlinFlagUtils.copyAllFlags(kmProperty, rewrittenKmProperty);
    KotlinFlagUtils.copyAllFlags(kmProperty.getGetter(), rewrittenKmProperty.getGetter());
    if (kmProperty.getSetter() != null) {
      rewrittenKmProperty.setSetter(new KmPropertyAccessorAttributes());
      KotlinFlagUtils.copyAllFlags(kmProperty.getSetter(), rewrittenKmProperty.getSetter());
    }
    boolean rewritten =
        rewriteIfNotNull(
            appView, returnType, rewrittenKmProperty::setReturnType, KotlinTypeInfo::rewrite);
    rewritten |=
        rewriteIfNotNull(
            appView,
            receiverParameterType,
            rewrittenKmProperty::setReceiverParameterType,
            KotlinTypeInfo::rewrite);
    rewritten |=
        rewriteIfNotNull(
            appView,
            setterParameter,
            rewrittenKmProperty::setSetterParameter,
            KotlinValueParameterInfo::rewrite);
    rewritten |=
        rewriteList(
            appView,
            typeParameters,
            rewrittenKmProperty.getTypeParameters(),
            KotlinTypeParameterInfo::rewrite);
    rewritten |=
        rewriteList(
            appView,
            contextReceiverTypes,
            rewrittenKmProperty.getContextReceiverTypes(),
            KotlinTypeInfo::rewrite);
    rewrittenKmProperty.getVersionRequirements().addAll(kmProperty.getVersionRequirements());
    if (fieldSignature != null) {
      rewritten |=
          fieldSignature.rewrite(
              newSignature -> JvmExtensionsKt.setFieldSignature(rewrittenKmProperty, newSignature),
              field,
              appView);
    }
    if (getterSignature != null) {
      rewritten |=
          getterSignature.rewrite(
              newSignature -> JvmExtensionsKt.setGetterSignature(rewrittenKmProperty, newSignature),
              getter,
              appView);
    }
    if (setterSignature != null) {
      rewritten |=
          setterSignature.rewrite(
              newSignature -> JvmExtensionsKt.setSetterSignature(rewrittenKmProperty, newSignature),
              setter,
              appView);
    }
    if (syntheticMethodForAnnotations != null) {
      rewritten |=
          syntheticMethodForAnnotations.rewrite(
              newSignature ->
                  JvmExtensionsKt.setSyntheticMethodForAnnotations(
                      rewrittenKmProperty, newSignature),
              syntheticMethodForAnnotationsMethod,
              appView);
    }
    rewritten |=
        rewriteIfNotNull(
            appView,
            syntheticMethodForDelegate,
            newMethod ->
                JvmExtensionsKt.setSyntheticMethodForDelegate(rewrittenKmProperty, newMethod),
            KotlinJvmMethodSignatureInfo::rewriteNoBacking);
    return rewritten;
  }

  @Override
  public void trace(KotlinMetadataUseRegistry registry) {
    returnType.trace(registry);
    if (receiverParameterType != null) {
      receiverParameterType.trace(registry);
    }
    if (setterParameter != null) {
      setterParameter.trace(registry);
    }
    forEachApply(typeParameters, param -> param::trace, registry);
    forEachApply(contextReceiverTypes, type -> type::trace, registry);
    if (fieldSignature != null) {
      fieldSignature.trace(registry);
    }
    if (getterSignature != null) {
      getterSignature.trace(registry);
    }
    if (setterSignature != null) {
      setterSignature.trace(registry);
    }
    if (syntheticMethodForAnnotations != null) {
      syntheticMethodForAnnotations.trace(registry);
    }
    if (syntheticMethodForDelegate != null) {
      syntheticMethodForDelegate.trace(registry);
    }
  }

  @Override
  public String toString() {
    return "KotlinPropertyInfo(" + kmProperty.getName() + ")";
  }
}
