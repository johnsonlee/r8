// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.KotlinMetadataUtils.updateJvmMetadataVersionIfRequired;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMember;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.utils.Pair;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import kotlin.Metadata;
import kotlin.metadata.KmPackage;
import kotlin.metadata.jvm.JvmMetadataVersion;
import kotlin.metadata.jvm.KotlinClassMetadata.FileFacade;

// Holds information about Metadata.FileFacade
public class KotlinFileFacadeInfo implements KotlinClassLevelInfo {

  private final FileFacade kmFileFacade;
  private final KotlinPackageInfo packageInfo;
  private final String packageName;

  private KotlinFileFacadeInfo(
      FileFacade kmFileFacade, KotlinPackageInfo packageInfo, String packageName) {
    this.kmFileFacade = kmFileFacade;
    this.packageInfo = packageInfo;
    this.packageName = packageName;
  }

  public static KotlinFileFacadeInfo create(
      FileFacade kmFileFacade,
      String packageName,
      DexClass clazz,
      AppView<?> appView,
      Consumer<DexEncodedMethod> keepByteCode,
      BiConsumer<DexEncodedMember<?, ?>, KotlinMemberLevelInfo> memberInfoConsumer) {
    KmPackage kmPackage = kmFileFacade.getKmPackage();
    return new KotlinFileFacadeInfo(
        kmFileFacade,
        KotlinPackageInfo.create(kmPackage, clazz, appView, keepByteCode, memberInfoConsumer),
        packageName);
  }

  @Override
  public boolean isFileFacade() {
    return true;
  }

  @Override
  public KotlinFileFacadeInfo asFileFacade() {
    return this;
  }

  @Override
  public Pair<Metadata, Boolean> rewrite(DexClass clazz, AppView<?> appView) {
    KmPackage kmPackage = new KmPackage();
    boolean rewritten = packageInfo.rewrite(kmPackage, clazz, appView);
    updateJvmMetadataVersionIfRequired(kmFileFacade);
    kmFileFacade.setKmPackage(kmPackage);
    return Pair.create(kmFileFacade.write(), rewritten);
  }

  @Override
  public String getPackageName() {
    return packageName;
  }

  public String getModuleName() {
    return packageInfo.getModuleName();
  }

  @Override
  public JvmMetadataVersion getMetadataVersion() {
    return kmFileFacade.getVersion();
  }

  @Override
  public void trace(KotlinMetadataUseRegistry registry) {
    packageInfo.trace(registry);
  }
}
