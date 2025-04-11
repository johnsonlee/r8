// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import java.util.function.Consumer;
import kotlin.metadata.KmProperty;

public class KotlinPropertyInfoDelegate implements KotlinPropertyInfo {

  private final ConcreteKotlinPropertyInfo delegate;
  private final PropertyType propertyType;

  public KotlinPropertyInfoDelegate(ConcreteKotlinPropertyInfo delegate, PropertyType type) {
    this.delegate = delegate;
    this.propertyType = type;
  }

  @Override
  public PropertyType getPropertyType() {
    return propertyType;
  }

  @Override
  public KotlinPropertyInfo getReference() {
    return delegate;
  }

  public enum PropertyType {
    SETTER,
    GETTER,
    SYNTHETIC_METHOD_FOR_ANNOTATIONS,
    UNKNOWN
  }

  @Override
  public KotlinJvmFieldSignatureInfo getFieldSignature() {
    return delegate.getFieldSignature();
  }

  @Override
  public KotlinJvmMethodSignatureInfo getGetterSignature() {
    return delegate.getGetterSignature();
  }

  @Override
  public KotlinJvmMethodSignatureInfo getSetterSignature() {
    return delegate.getSetterSignature();
  }

  @Override
  public boolean rewriteNoBacking(Consumer<KmProperty> consumer, AppView<?> appView) {
    return delegate.rewriteNoBacking(consumer, appView);
  }

  @Override
  public boolean rewrite(
      Consumer<KmProperty> consumer,
      DexEncodedField field,
      DexEncodedMethod getter,
      DexEncodedMethod setter,
      DexEncodedMethod syntheticMethodForAnnotationsMethod,
      AppView<?> appView) {
    return delegate.rewrite(
        consumer, field, getter, setter, syntheticMethodForAnnotationsMethod, appView);
  }

  @Override
  public void trace(KotlinMetadataUseRegistry registry) {
    delegate.trace(registry);
  }

  @Override
  public String toString() {
    return "Del" + delegate.toString();
  }
}
