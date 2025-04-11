// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.KotlinMetadataUtils.getKotlinLocalOrAnonymousNameFromDescriptor;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.kotlin.Kotlin.ClassClassifiers;
import com.android.tools.r8.shaking.EnqueuerMetadataTraceable;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.Reporter;
import kotlin.metadata.KmClassifier;
import kotlin.metadata.KmClassifier.TypeAlias;
import kotlin.metadata.KmClassifier.TypeParameter;
import kotlin.metadata.KmType;

public abstract class KotlinClassifierInfo implements EnqueuerMetadataTraceable {

  public static KotlinClassifierInfo create(
      KmClassifier classifier, DexItemFactory factory, Reporter reporter) {
    if (classifier instanceof KmClassifier.Class) {
      String originalTypeName = ((KmClassifier.Class) classifier).getName();
      // If this name starts with '.', it represents a local class or an anonymous object. This is
      // used by the Kotlin compiler to prevent lookup of this name in the resolution:
      // .kotlin/random/FallbackThreadLocalRandom$implStorage$1
      boolean isLocalOrAnonymous = originalTypeName.startsWith(".");
      String descriptor =
          DescriptorUtils.getDescriptorFromKotlinClassifier(
              isLocalOrAnonymous ? originalTypeName.substring(1) : originalTypeName);
      if (DescriptorUtils.isClassDescriptor(descriptor)) {
        return new KotlinClassClassifierInfo(
            KotlinTypeReference.fromDescriptor(descriptor, factory), isLocalOrAnonymous);
      } else {
        return new KotlinUnknownClassClassifierInfo(originalTypeName);
      }
    } else if (classifier instanceof KmClassifier.TypeAlias) {
      return new KotlinTypeAliasClassifierInfo(((TypeAlias) classifier).getName());
    } else if (classifier instanceof KmClassifier.TypeParameter) {
      return new KotlinTypeParameterClassifierInfo(((TypeParameter) classifier).getId());
    } else {
      reporter.warning(KotlinMetadataDiagnostic.unknownClassifier(classifier.toString()));
      return new KotlinUnknownClassifierInfo(classifier.toString());
    }
  }

  abstract boolean rewrite(KmType kmType, AppView<?> appView);

  public DexType rewriteType(GraphLens graphLens, GraphLens codeLens) {
    return null;
  }

  public static class KotlinClassClassifierInfo extends KotlinClassifierInfo {

    private final KotlinTypeReference type;
    private final boolean isLocalOrAnonymous;

    private KotlinClassClassifierInfo(KotlinTypeReference type, boolean isLocalOrAnonymous) {
      this.type = type;
      this.isLocalOrAnonymous = isLocalOrAnonymous;
    }

    @Override
    boolean rewrite(KmType kmType, AppView<?> appView) {
      return type.toRenamedDescriptorOrDefault(
          descriptor ->
              kmType.setClassifier(
                  new KmClassifier.Class(
                      getKotlinLocalOrAnonymousNameFromDescriptor(descriptor, isLocalOrAnonymous))),
          appView,
          ClassClassifiers.anyDescriptor);
    }

    @Override
    public void trace(KotlinMetadataUseRegistry registry) {
      type.trace(registry);
    }

    @Override
    public DexType rewriteType(GraphLens graphLens, GraphLens codeLens) {
      return type.rewriteType(graphLens, codeLens);
    }
  }

  public static class KotlinTypeParameterClassifierInfo extends KotlinClassifierInfo {

    private final int typeId;

    private KotlinTypeParameterClassifierInfo(int typeId) {
      this.typeId = typeId;
    }

    @Override
    boolean rewrite(KmType kmType, AppView<?> appView) {
      kmType.setClassifier(new KmClassifier.TypeParameter(typeId));
      return false;
    }

    @Override
    public void trace(KotlinMetadataUseRegistry registry) {
      // Intentionally empty.
    }
  }

  public static class KotlinTypeAliasClassifierInfo extends KotlinClassifierInfo {

    private final String typeAlias;

    private KotlinTypeAliasClassifierInfo(String typeAlias) {
      this.typeAlias = typeAlias;
    }

    @Override
    boolean rewrite(KmType kmType, AppView<?> appView) {
      kmType.setClassifier(new KmClassifier.TypeAlias(typeAlias));
      return false;
    }

    @Override
    public void trace(KotlinMetadataUseRegistry registry) {
      // Intentionally empty.
    }
  }

  public static class KotlinUnknownClassClassifierInfo extends KotlinClassifierInfo {
    private final String classifier;

    private KotlinUnknownClassClassifierInfo(String classifier) {
      this.classifier = classifier;
    }

    @Override
    boolean rewrite(KmType kmType, AppView<?> appView) {
      kmType.setClassifier(new KmClassifier.Class(classifier));
      return false;
    }

    @Override
    public void trace(KotlinMetadataUseRegistry registry) {
      // Intentionally empty.
    }
  }

  public static class KotlinUnknownClassifierInfo extends KotlinClassifierInfo {
    private final String classifier;

    private KotlinUnknownClassifierInfo(String classifier) {
      this.classifier = classifier;
    }

    @Override
    boolean rewrite(KmType kmType, AppView<?> appView) {
      kmType.setClassifier(new KmClassifier.TypeAlias(classifier));
      return false;
    }

    @Override
    public void trace(KotlinMetadataUseRegistry registry) {
      // Intentionally empty.
    }
  }
}
