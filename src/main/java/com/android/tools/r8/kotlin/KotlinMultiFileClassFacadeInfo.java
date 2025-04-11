// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.KotlinMetadataUtils.updateJvmMetadataVersionIfRequired;
import static com.android.tools.r8.utils.FunctionUtils.forEachApply;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.utils.Pair;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import kotlin.Metadata;
import kotlin.metadata.jvm.JvmMetadataVersion;
import kotlin.metadata.jvm.KotlinClassMetadata.MultiFileClassFacade;

// Holds information about Metadata.MultiFileClassFace
public class KotlinMultiFileClassFacadeInfo implements KotlinClassLevelInfo {

  private final MultiFileClassFacade kmMultiFileClassFacade;
  private final List<KotlinTypeReference> partClassNames;
  private final String packageName;

  private KotlinMultiFileClassFacadeInfo(
      MultiFileClassFacade kmMultiFileClassFacade,
      List<KotlinTypeReference> partClassNames,
      String packageName) {
    this.kmMultiFileClassFacade = kmMultiFileClassFacade;
    this.partClassNames = partClassNames;
    this.packageName = packageName;
  }

  static KotlinMultiFileClassFacadeInfo create(
      MultiFileClassFacade kmMultiFileClassFacade,
      String packageName,
      DexItemFactory factory) {
    ImmutableList.Builder<KotlinTypeReference> builder = ImmutableList.builder();
    for (String partClassName : kmMultiFileClassFacade.getPartClassNames()) {
      builder.add(
          KotlinTypeReference.fromBinaryNameOrKotlinClassifier(
              partClassName, factory, partClassName));
    }
    return new KotlinMultiFileClassFacadeInfo(kmMultiFileClassFacade, builder.build(), packageName);
  }

  @Override
  public boolean isMultiFileFacade() {
    return true;
  }

  @Override
  public KotlinMultiFileClassFacadeInfo asMultiFileFacade() {
    return this;
  }

  @Override
  public Pair<Metadata, Boolean> rewrite(DexClass clazz, AppView<?> appView) {
    List<String> partClassNameStrings = new ArrayList<>(partClassNames.size());
    boolean rewritten = false;
    for (KotlinTypeReference partClassName : partClassNames) {
      rewritten |=
          partClassName.toRenamedBinaryNameOrDefault(
              binaryName -> {
                if (binaryName != null) {
                  partClassNameStrings.add(binaryName);
                }
              },
              appView,
              null);
    }
    kmMultiFileClassFacade.setPartClassNames(partClassNameStrings);
    updateJvmMetadataVersionIfRequired(kmMultiFileClassFacade);
    return Pair.create(kmMultiFileClassFacade.write(), rewritten);
  }

  @Override
  public String getPackageName() {
    return packageName;
  }

  @Override
  public JvmMetadataVersion getMetadataVersion() {
    return kmMultiFileClassFacade.getVersion();
  }

  @Override
  public void trace(KotlinMetadataUseRegistry registry) {
    forEachApply(partClassNames, type -> type::trace, registry);
  }
}
