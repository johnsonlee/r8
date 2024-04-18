// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.kotlin.KotlinPropertyInfoDelegate.PropertyType;
import java.util.function.Consumer;
import kotlinx.metadata.KmProperty;

public interface KotlinPropertyInfo extends KotlinFieldLevelInfo, KotlinMethodLevelInfo {

  default PropertyType getPropertyType() {
    return PropertyType.UNKNOWN;
  }

  @Override
  default boolean isProperty() {
    return true;
  }

  @Override
  default KotlinPropertyInfo asProperty() {
    return this;
  }

  default KotlinPropertyInfo getReference() {
    return this;
  }

  KotlinJvmFieldSignatureInfo getFieldSignature();

  KotlinJvmMethodSignatureInfo getGetterSignature();

  KotlinJvmMethodSignatureInfo getSetterSignature();

  boolean rewriteNoBacking(Consumer<KmProperty> consumer, AppView<?> appView);

  boolean rewrite(
      Consumer<KmProperty> consumer,
      DexEncodedField field,
      DexEncodedMethod getter,
      DexEncodedMethod setter,
      DexEncodedMethod syntheticMethodForAnnotationsMethod,
      AppView<?> appView);
}
