// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.errors.InvalidDescriptorException;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.InnerClassAttribute;
import com.android.tools.r8.shaking.ProguardConfiguration;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import com.android.tools.r8.shaking.ProguardKeepRule;
import com.android.tools.r8.shaking.ProguardKeepRuleType;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.TriFunction;
import java.util.List;
import java.util.function.Consumer;
import kotlin.Metadata;
import kotlin.metadata.KmProperty;
import kotlin.metadata.jvm.JvmExtensionsKt;
import kotlin.metadata.jvm.JvmFieldSignature;
import kotlin.metadata.jvm.JvmMetadataVersion;
import kotlin.metadata.jvm.JvmMethodSignature;
import kotlin.metadata.jvm.KotlinClassMetadata;

public class KotlinMetadataUtils {

  public static final JvmMetadataVersion VERSION_1_4_0 = new JvmMetadataVersion(1, 4, 0);
  private static final NoKotlinInfo NO_KOTLIN_INFO = new NoKotlinInfo("NO_KOTLIN_INFO");
  private static final NoKotlinInfo INVALID_KOTLIN_INFO = new NoKotlinInfo("INVALID_KOTLIN_INFO");

  private static class NoKotlinInfo
      implements KotlinClassLevelInfo, KotlinFieldLevelInfo, KotlinMethodLevelInfo {

    private final String name;

    private NoKotlinInfo(String name) {
      this.name = name;
    }

    @Override
    public String toString() {
      return name;
    }

    @Override
    public Pair<Metadata, Boolean> rewrite(DexClass clazz, AppView<?> appView) {
      throw new Unreachable("Should never be called");
    }

    @Override
    public String getPackageName() {
      throw new Unreachable("Should never be called");
    }

    @Override
    public JvmMetadataVersion getMetadataVersion() {
      throw new Unreachable("Should never be called");
    }

    @Override
    public boolean isNoKotlinInformation() {
      return true;
    }

    @Override
    public void trace(KotlinMetadataUseRegistry registry) {
      // No information needed to trace.
    }
  }

  public static NoKotlinInfo getNoKotlinInfo() {
    return NO_KOTLIN_INFO;
  }

  public static NoKotlinInfo getInvalidKotlinInfo() {
    return INVALID_KOTLIN_INFO;
  }

  static JvmFieldSignature toJvmFieldSignature(DexField field) {
    return new JvmFieldSignature(field.name.toString(), field.type.toDescriptorString());
  }

  static JvmMethodSignature toJvmMethodSignature(DexMethod method) {
    StringBuilder descBuilder = new StringBuilder();
    descBuilder.append("(");
    for (DexType argType : method.proto.parameters.values) {
      descBuilder.append(argType.toDescriptorString());
    }
    descBuilder.append(")");
    descBuilder.append(method.proto.returnType.toDescriptorString());
    return new JvmMethodSignature(method.name.toString(), descBuilder.toString());
  }

  static JvmMethodSignature toDefaultJvmMethodSignature(
      JvmMethodSignature methodSignature, int intArguments) {
    return new JvmMethodSignature(
        methodSignature.getName() + "$default",
        methodSignature
            .getDescriptor()
            .replace(")", "I".repeat(intArguments) + "Ljava/lang/Object;)"));
  }

  static class KmPropertyProcessor {
    private final JvmFieldSignature fieldSignature;
    // Custom getter via @get:JvmName("..."). Otherwise, null.
    private final JvmMethodSignature getterSignature;
    // Custom getter via @set:JvmName("..."). Otherwise, null.
    private final JvmMethodSignature setterSignature;
    private final JvmMethodSignature syntheticMethodForAnnotationsSignature;

    KmPropertyProcessor(KmProperty kmProperty) {
      fieldSignature = JvmExtensionsKt.getFieldSignature(kmProperty);
      getterSignature = JvmExtensionsKt.getGetterSignature(kmProperty);
      setterSignature = JvmExtensionsKt.getSetterSignature(kmProperty);
      syntheticMethodForAnnotationsSignature =
          JvmExtensionsKt.getSyntheticMethodForAnnotations(kmProperty);
    }

    JvmFieldSignature fieldSignature() {
      return fieldSignature;
    }

    JvmMethodSignature getterSignature() {
      return getterSignature;
    }

    JvmMethodSignature setterSignature() {
      return setterSignature;
    }

    public JvmMethodSignature syntheticMethodForAnnotationsSignature() {
      return syntheticMethodForAnnotationsSignature;
    }
  }

  static boolean isValidMethodDescriptor(String methodDescriptor) {
    try {
      String[] argDescriptors = DescriptorUtils.getArgumentTypeDescriptors(methodDescriptor);
      for (String argDescriptor : argDescriptors) {
        if (argDescriptor.charAt(0) == 'L' && !DescriptorUtils.isClassDescriptor(argDescriptor)) {
          return false;
        }
      }
      return true;
    } catch (InvalidDescriptorException e) {
      return false;
    }
  }

  public static boolean mayProcessKotlinMetadata(AppView<?> appView) {
    // This can run before we have determined the pinned items, because we may need to load the
    // stack-map table on input. This is therefore a conservative guess on kotlin.Metadata is kept.
    DexClass kotlinMetadata =
        appView
            .appInfo()
            .definitionForWithoutExistenceAssert(appView.dexItemFactory().kotlinMetadataType);
    if (kotlinMetadata == null || kotlinMetadata.isNotProgramClass()) {
      return true;
    }
    ProguardConfiguration proguardConfiguration = appView.options().getProguardConfiguration();
    if (proguardConfiguration != null && proguardConfiguration.getRules() != null) {
      for (ProguardConfigurationRule rule : proguardConfiguration.getRules()) {
        if (KotlinMetadataUtils.canBeKotlinMetadataKeepRule(rule, appView.options().itemFactory)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean canBeKotlinMetadataKeepRule(
      ProguardConfigurationRule rule, DexItemFactory factory) {
    if (rule.isProguardIfRule()) {
      // For if rules, we simply assume that the precondition can become true.
      return canBeKotlinMetadataKeepRule(rule.asProguardIfRule().getSubsequentRule(), factory);
    }
    if (!rule.isProguardKeepRule()) {
      return false;
    }
    ProguardKeepRule proguardKeepRule = rule.asProguardKeepRule();
    // -keepclassmembers will not in itself keep a class alive.
    if (proguardKeepRule.getType() == ProguardKeepRuleType.KEEP_CLASS_MEMBERS) {
      return false;
    }
    // If the rule allows shrinking, it will not require us to keep the class.
    if (proguardKeepRule.getModifiers().allowsShrinking) {
      return false;
    }
    // Check if the type is matched
    return proguardKeepRule.getClassNames().matches(factory.kotlinMetadataType);
  }

  static String getKotlinClassName(DexClass clazz, String descriptor) {
    InnerClassAttribute innerClassAttribute = clazz.getInnerClassAttributeForThisClass();
    if (innerClassAttribute != null && innerClassAttribute.getOuter() != null) {
      return DescriptorUtils.descriptorToKotlinClassifier(descriptor);
    } else if (clazz.isLocalClass() || clazz.isAnonymousClass()) {
      return getKotlinLocalOrAnonymousNameFromDescriptor(descriptor, true);
    } else {
      // We no longer have an innerclass relationship to maintain and we therefore return a binary
      // name.
      return DescriptorUtils.getBinaryNameFromDescriptor(descriptor);
    }
  }

  static String getKotlinLocalOrAnonymousNameFromDescriptor(
      String descriptor, boolean isLocalOrAnonymous) {
    // For local or anonymous classes, the classifier is prefixed with '.' and inner classes
    // are separated with '$'.
    if (isLocalOrAnonymous) {
      return "." + DescriptorUtils.getBinaryNameFromDescriptor(descriptor);
    }
    return DescriptorUtils.descriptorToKotlinClassifier(descriptor);
  }

  public static void updateJvmMetadataVersionIfRequired(KotlinClassMetadata metadata) {
    if (metadata.getVersion().compareTo(VERSION_1_4_0) < 0) {
      metadata.setVersion(VERSION_1_4_0);
    }
  }

  static <TInfo, TKm> boolean rewriteIfNotNull(
      AppView<?> appView,
      TInfo info,
      Consumer<TKm> newTConsumer,
      TriFunction<TInfo, Consumer<TKm>, AppView<?>, Boolean> rewrite) {
    return info != null ? rewrite.apply(info, newTConsumer, appView) : false;
  }

  static <TInfo, TKm> boolean rewriteList(
      AppView<?> appView,
      List<TInfo> ts,
      List<TKm> newTs,
      TriFunction<TInfo, Consumer<TKm>, AppView<?>, Boolean> rewrite) {
    assert newTs.isEmpty();
    return rewriteList(appView, ts, newTs::add, rewrite);
  }

  static <TInfo, TKm> boolean rewriteList(
      AppView<?> appView,
      List<TInfo> ts,
      Consumer<TKm> newTConsumer,
      TriFunction<TInfo, Consumer<TKm>, AppView<?>, Boolean> rewrite) {
    boolean rewritten = false;
    for (TInfo t : ts) {
      rewritten |= rewrite.apply(t, newTConsumer, appView);
    }
    return rewritten;
  }
}
