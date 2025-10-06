// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.KotlinMetadataUtils.isValidMethodDescriptor;
import static com.android.tools.r8.kotlin.KotlinMetadataUtils.rewriteList;
import static com.android.tools.r8.kotlin.KotlinMetadataUtils.toDefaultJvmMethodSignature;
import static com.android.tools.r8.utils.FunctionUtils.forEachApply;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMember;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.kotlin.KotlinMetadataUtils.KmPropertyProcessor;
import com.android.tools.r8.kotlin.KotlinPropertyInfoDelegate.PropertyType;
import com.android.tools.r8.shaking.EnqueuerMetadataTraceable;
import com.android.tools.r8.utils.Reporter;
import com.google.common.collect.ImmutableList;
import com.google.common.math.IntMath;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import kotlin.metadata.Attributes;
import kotlin.metadata.KmDeclarationContainer;
import kotlin.metadata.KmFunction;
import kotlin.metadata.KmProperty;
import kotlin.metadata.KmPropertyAccessorAttributes;
import kotlin.metadata.KmTypeAlias;
import kotlin.metadata.jvm.JvmExtensionsKt;
import kotlin.metadata.jvm.JvmMethodSignature;

// Holds information about KmDeclarationContainer
public class KotlinDeclarationContainerInfo implements EnqueuerMetadataTraceable {

  private final List<KotlinTypeAliasInfo> typeAliases;
  // The functions in notBackedFunctions are KmFunctions where we could not find a representative.
  private final List<KotlinFunctionInfo> functionsWithNoBacking;
  // The properties in propertiesWithNoBacking are KmProperties where we could not find a getter,
  // setter or backing field.
  private final List<KotlinPropertyInfo> propertiesWithNoBacking;

  private KotlinDeclarationContainerInfo(
      List<KotlinTypeAliasInfo> typeAliases,
      List<KotlinFunctionInfo> functionsWithNoBacking,
      List<KotlinPropertyInfo> propertiesWithNoBacking) {
    this.typeAliases = typeAliases;
    this.functionsWithNoBacking = functionsWithNoBacking;
    this.propertiesWithNoBacking = propertiesWithNoBacking;
  }

  public static KotlinDeclarationContainerInfo create(
      KmDeclarationContainer container,
      Map<String, DexEncodedMethod> methodSignatureMap,
      Map<String, DexEncodedField> fieldSignatureMap,
      DexItemFactory factory,
      Reporter reporter,
      Consumer<DexEncodedMethod> keepByteCode,
      BiConsumer<DexEncodedMember<?, ?>, KotlinMemberLevelInfo> memberInfoConsumer,
      KotlinMetadataMembersTracker originalAssignmentTracker) {
    ImmutableList.Builder<KotlinFunctionInfo> notBackedFunctions = ImmutableList.builder();
    for (KmFunction kmFunction : container.getFunctions()) {
      JvmMethodSignature signature = JvmExtensionsKt.getSignature(kmFunction);
      if (signature == null) {
        assert false;
        continue;
      }
      KotlinFunctionInfo kotlinFunctionInfo =
          KotlinFunctionInfo.create(kmFunction, factory, reporter);
      DexEncodedMethod method = methodSignatureMap.get(signature.toString());
      if (method == null) {
        notBackedFunctions.add(kotlinFunctionInfo);
        if (!isValidMethodDescriptor(signature.getDescriptor())) {
          // TODO(b/155536535): Enable this assert.
          // appView
          //     .options()
          //     .reporter
          //     .info(KotlinMetadataDiagnostic.invalidMethodDescriptor(signature.asString()));
        } else {
          // TODO(b/154348568): Enable the assertion below.
          // assert false : "Could not find method with signature " + signature.asString();
        }
        continue;
      }
      keepIfInline(kmFunction, method, signature, methodSignatureMap, keepByteCode);
      memberInfoConsumer.accept(method, kotlinFunctionInfo);
      originalAssignmentTracker.add(method.getReference());
    }

    ImmutableList.Builder<KotlinPropertyInfo> notBackedProperties = ImmutableList.builder();
    for (KmProperty kmProperty : container.getProperties()) {
      ConcreteKotlinPropertyInfo kotlinPropertyInfo =
          ConcreteKotlinPropertyInfo.create(kmProperty, factory, reporter);
      KmPropertyProcessor propertyProcessor = new KmPropertyProcessor(kmProperty);
      boolean hasBacking = false;
      if (propertyProcessor.fieldSignature() != null) {
        DexEncodedField field =
            fieldSignatureMap.get(propertyProcessor.fieldSignature().toString());
        if (field != null) {
          hasBacking = true;
          field.setKotlinMemberInfo(kotlinPropertyInfo);
          originalAssignmentTracker.add(field.getReference());
        }
      }
      if (propertyProcessor.getterSignature() != null) {
        DexEncodedMethod method =
            methodSignatureMap.get(propertyProcessor.getterSignature().toString());
        if (method != null) {
          hasBacking = true;
          keepIfAccessorInline(kmProperty.getGetter(), method, keepByteCode);
          memberInfoConsumer.accept(
              method, new KotlinPropertyInfoDelegate(kotlinPropertyInfo, PropertyType.GETTER));
          originalAssignmentTracker.add(method.getReference());
        }
      }
      if (propertyProcessor.setterSignature() != null) {
        DexEncodedMethod method =
            methodSignatureMap.get(propertyProcessor.setterSignature().toString());
        if (method != null) {
          hasBacking = true;
          keepIfAccessorInline(kmProperty.getGetter(), method, keepByteCode);
          memberInfoConsumer.accept(
              method, new KotlinPropertyInfoDelegate(kotlinPropertyInfo, PropertyType.SETTER));
          originalAssignmentTracker.add(method.getReference());
        }
      }
      if (propertyProcessor.syntheticMethodForAnnotationsSignature() != null) {
        DexEncodedMethod method =
            methodSignatureMap.get(
                propertyProcessor.syntheticMethodForAnnotationsSignature().toString());
        if (method != null) {
          hasBacking = true;
          memberInfoConsumer.accept(
              method,
              new KotlinPropertyInfoDelegate(
                  kotlinPropertyInfo, PropertyType.SYNTHETIC_METHOD_FOR_ANNOTATIONS));
          originalAssignmentTracker.add(method.getReference());
        }
      }
      if (!hasBacking) {
        notBackedProperties.add(kotlinPropertyInfo);
      }
    }
    return new KotlinDeclarationContainerInfo(
        getTypeAliases(container.getTypeAliases(), factory, reporter),
        notBackedFunctions.build(),
        notBackedProperties.build());
  }

  private static void keepIfInline(
      KmFunction kmFunction,
      DexEncodedMethod method,
      JvmMethodSignature signature,
      Map<String, DexEncodedMethod> methodSignatureMap,
      Consumer<DexEncodedMethod> keepByteCode) {
    if (Attributes.isInline(kmFunction)) {
      // Check if we can find a default method. If there are more than 32 arguments another int
      // index will be added to the default method.
      for (int i = 1;
          i <= IntMath.divide(method.getParameters().size(), 32, RoundingMode.CEILING);
          i++) {
        DexEncodedMethod defaultValueMethod =
            methodSignatureMap.get(toDefaultJvmMethodSignature(signature, i).toString());
        if (defaultValueMethod != null) {
          keepByteCode.accept(defaultValueMethod);
          return;
        }
      }
      String forInlineSig = signature.getName() + "$$forInline" + signature.getDescriptor();
      if (methodSignatureMap.containsKey(forInlineSig)) {
        keepByteCode.accept(methodSignatureMap.get(forInlineSig));
      }
      keepByteCode.accept(method);
    }
  }

  private static void keepIfAccessorInline(
      KmPropertyAccessorAttributes kmPropertyAccessorAttributes,
      DexEncodedMethod method,
      Consumer<DexEncodedMethod> keepByteCode) {
    if (Attributes.isInline(kmPropertyAccessorAttributes)) {
      keepByteCode.accept(method);
    }
  }

  private static List<KotlinTypeAliasInfo> getTypeAliases(
      List<KmTypeAlias> aliases, DexItemFactory factory, Reporter reporter) {
    ImmutableList.Builder<KotlinTypeAliasInfo> builder = ImmutableList.builder();
    for (KmTypeAlias alias : aliases) {
      builder.add(KotlinTypeAliasInfo.create(alias, factory, reporter));
    }
    return builder.build();
  }

  @SuppressWarnings("ReferenceEquality")
  boolean rewrite(
      Consumer<KmFunction> functionConsumer,
      Consumer<KmProperty> propertyConsumer,
      Consumer<KmTypeAlias> typeAliasConsumer,
      DexClass clazz,
      AppView<?> appView,
      KotlinMetadataMembersTracker rewrittenMembersWithKotlinInfo) {
    // Type aliases only have a representation here, so we can generate them directly.
    boolean rewritten =
        rewriteList(appView, typeAliases, typeAliasConsumer, KotlinTypeAliasInfo::rewrite);
    // For properties, we need to combine potentially a field, setter and getter.
    Map<KotlinPropertyInfo, KotlinPropertyGroup> properties = new LinkedHashMap<>();
    for (DexEncodedField field : clazz.fields()) {
      if (field.getKotlinInfo().isProperty()) {
        properties
            .computeIfAbsent(
                field.getKotlinInfo().asProperty(), ignored -> new KotlinPropertyGroup())
            .setBackingField(field);
        rewrittenMembersWithKotlinInfo.add(field.getReference());
      }
    }
    for (DexEncodedMethod method : clazz.methods()) {
      if (method.getKotlinInfo().isFunction()) {
        rewritten |= method.getKotlinInfo().asFunction().rewrite(functionConsumer, method, appView);
        rewrittenMembersWithKotlinInfo.add(method.getReference());
        continue;
      }
      KotlinPropertyInfo kotlinPropertyInfo = method.getKotlinInfo().asProperty();
      if (kotlinPropertyInfo == null) {
        continue;
      }
      rewrittenMembersWithKotlinInfo.add(method.getReference());
      KotlinPropertyGroup kotlinPropertyGroup =
          properties.computeIfAbsent(
              kotlinPropertyInfo.getReference(), ignored -> new KotlinPropertyGroup());
      switch (kotlinPropertyInfo.getPropertyType()) {
        case GETTER:
          kotlinPropertyGroup.setGetter(method);
          break;
        case SETTER:
          kotlinPropertyGroup.setSetter(method);
          break;
        case SYNTHETIC_METHOD_FOR_ANNOTATIONS:
          kotlinPropertyGroup.setSyntheticMethodForAnnotations(method);
          break;
        default:
          // Do nothing.
      }
    }
    for (KotlinPropertyInfo kotlinPropertyInfo : properties.keySet()) {
      KotlinPropertyGroup kotlinPropertyGroup = properties.get(kotlinPropertyInfo);
      rewritten |=
          kotlinPropertyInfo.rewrite(
              propertyConsumer,
              kotlinPropertyGroup.backingField,
              kotlinPropertyGroup.getter,
              kotlinPropertyGroup.setter,
              kotlinPropertyGroup.syntheticMethodForAnnotations,
              appView);
    }
    // Add all not backed functions and properties.
    rewritten |=
        rewriteList(
            appView,
            functionsWithNoBacking,
            functionConsumer,
            KotlinFunctionInfo::rewriteNoBacking);
    rewritten |=
        rewriteList(
            appView,
            propertiesWithNoBacking,
            propertyConsumer,
            KotlinPropertyInfo::rewriteNoBacking);
    return rewritten;
  }

  @Override
  public void trace(KotlinMetadataUseRegistry registry) {
    forEachApply(typeAliases, alias -> alias::trace, registry);
    forEachApply(functionsWithNoBacking, function -> function::trace, registry);
    forEachApply(propertiesWithNoBacking, property -> property::trace, registry);
  }

  public static class KotlinPropertyGroup {

    private DexEncodedField backingField = null;
    private DexEncodedMethod setter = null;
    private DexEncodedMethod getter = null;
    private DexEncodedMethod syntheticMethodForAnnotations = null;

    void setBackingField(DexEncodedField backingField) {
      assert this.backingField == null;
      this.backingField = backingField;
    }

    void setGetter(DexEncodedMethod getter) {
      assert this.getter == null;
      this.getter = getter;
    }

    void setSetter(DexEncodedMethod setter) {
      assert this.setter == null;
      this.setter = setter;
    }

    public void setSyntheticMethodForAnnotations(DexEncodedMethod syntheticMethodForAnnotations) {
      assert this.syntheticMethodForAnnotations == null;
      this.syntheticMethodForAnnotations = syntheticMethodForAnnotations;
    }
  }
}
