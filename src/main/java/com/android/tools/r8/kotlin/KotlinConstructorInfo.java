// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.KotlinMetadataUtils.rewriteList;
import static com.android.tools.r8.utils.FunctionUtils.forEachApply;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.utils.Reporter;
import java.util.List;
import kotlin.metadata.KmClass;
import kotlin.metadata.KmConstructor;
import kotlin.metadata.jvm.JvmExtensionsKt;

// Holds information about a KmConstructor object.
public class KotlinConstructorInfo implements KotlinMethodLevelInfo {

  // Information from original constructor.
  private final KmConstructor kmConstructor;
  // Information about the value parameters.
  private final List<KotlinValueParameterInfo> valueParameters;
  // Information about version requirements.
  private final KotlinJvmMethodSignatureInfo signature;

  private KotlinConstructorInfo(
      KmConstructor kmConstructor,
      List<KotlinValueParameterInfo> valueParameters,
      KotlinJvmMethodSignatureInfo signature) {
    this.kmConstructor = kmConstructor;
    this.valueParameters = valueParameters;
    this.signature = signature;
  }

  public static KotlinConstructorInfo create(
      KmConstructor kmConstructor, DexItemFactory factory, Reporter reporter) {
    return new KotlinConstructorInfo(
        kmConstructor,
        KotlinValueParameterInfo.create(kmConstructor.getValueParameters(), factory, reporter),
        KotlinJvmMethodSignatureInfo.create(JvmExtensionsKt.getSignature(kmConstructor), factory));
  }

  boolean rewrite(KmClass kmClass, DexEncodedMethod method, AppView<?> appView) {
    // Note that JvmExtensionsKt.setSignature does not have an overload for KmConstructorVisitor,
    // thus we rely on creating the KmConstructor manually.
    KmConstructor rewrittenKmConstructor = new KmConstructor();
    KotlinFlagUtils.copyAllFlags(kmConstructor, rewrittenKmConstructor);
    boolean rewritten = false;
    if (signature != null) {
      rewritten =
          signature.rewrite(
              rewrittenSignature ->
                  JvmExtensionsKt.setSignature(rewrittenKmConstructor, rewrittenSignature),
              method,
              appView);
    }
    rewritten |=
        rewriteList(
            appView,
            valueParameters,
            rewrittenKmConstructor.getValueParameters(),
            KotlinValueParameterInfo::rewrite);
    rewrittenKmConstructor.getVersionRequirements().addAll(kmConstructor.getVersionRequirements());
    kmClass.getConstructors().add(rewrittenKmConstructor);
    return rewritten;
  }

  @Override
  public boolean isConstructor() {
    return true;
  }

  @Override
  public KotlinConstructorInfo asConstructor() {
    return this;
  }

  @Override
  public void trace(KotlinMetadataUseRegistry registry) {
    forEachApply(valueParameters, param -> param::trace, registry);
    if (signature != null) {
      signature.trace(registry);
    }
  }
}
