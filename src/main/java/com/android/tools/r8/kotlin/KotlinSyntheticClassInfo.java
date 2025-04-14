// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.KotlinMetadataUtils.updateJvmMetadataVersionIfRequired;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMember;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.Pair;
import java.util.function.BiConsumer;
import kotlin.Metadata;
import kotlin.metadata.KmLambda;
import kotlin.metadata.jvm.JvmMetadataVersion;
import kotlin.metadata.jvm.KotlinClassMetadata.SyntheticClass;

// Holds information about a Metadata.SyntheticClass object.
public class KotlinSyntheticClassInfo implements KotlinClassLevelInfo {

  private final SyntheticClass syntheticClass;
  private final KotlinLambdaInfo lambda;
  private final String packageName;

  public enum Flavour {
    KotlinStyleLambda,
    JavaStyleLambda,
    Unclassified
  }

  private final Flavour flavour;

  private KotlinSyntheticClassInfo(
      SyntheticClass syntheticClass, KotlinLambdaInfo lambda, Flavour flavour, String packageName) {
    this.syntheticClass = syntheticClass;
    this.lambda = lambda;
    this.flavour = flavour;
    this.packageName = packageName;
  }

  static KotlinSyntheticClassInfo create(
      SyntheticClass syntheticClass,
      String packageName,
      DexClass clazz,
      Kotlin kotlin,
      AppView<?> appView,
      BiConsumer<DexEncodedMember<?, ?>, KotlinMemberLevelInfo> memberInfoConsumer) {
    KmLambda lambda = syntheticClass.getKmLambda();
    assert lambda == null || syntheticClass.isLambda();
    return new KotlinSyntheticClassInfo(
        syntheticClass,
        lambda != null
            ? KotlinLambdaInfo.create(
                clazz, lambda, appView.dexItemFactory(), memberInfoConsumer, appView.reporter())
            : null,
        getFlavour(clazz, kotlin),
        packageName);
  }

  public boolean isLambda() {
    return lambda != null && flavour != Flavour.Unclassified;
  }

  @Override
  public boolean isSyntheticClass() {
    return true;
  }

  @Override
  public KotlinSyntheticClassInfo asSyntheticClass() {
    return this;
  }

  @Override
  public Pair<Metadata, Boolean> rewrite(DexClass clazz, AppView<?> appView) {
    updateJvmMetadataVersionIfRequired(syntheticClass);
    if (lambda == null) {
      return Pair.create(syntheticClass.write(), false);
    }
    Box<KmLambda> newLambda = new Box<>();
    boolean rewritten = lambda.rewrite(newLambda::set, clazz, appView);
    assert newLambda.isSet();
    syntheticClass.setKmLambda(newLambda.get());
    syntheticClass.setFlags(0);
    return Pair.create(syntheticClass.write(), rewritten);
  }

  @Override
  public void trace(KotlinMetadataUseRegistry registry) {
    if (lambda != null) {
      lambda.trace(registry);
    }
  }

  @Override
  public String getPackageName() {
    return packageName;
  }

  @Override
  public JvmMetadataVersion getMetadataVersion() {
    return syntheticClass.getVersion();
  }

  @SuppressWarnings("ReferenceEquality")
  public static Flavour getFlavour(DexClass clazz, Kotlin kotlin) {
    // Returns KotlinStyleLambda if the given clazz has shape of a Kotlin-style lambda:
    //   a class that directly extends kotlin.jvm.internal.Lambda
    if (clazz.superType == kotlin.functional.lambdaType) {
      return Flavour.KotlinStyleLambda;
    }
    // Returns JavaStyleLambda if the given clazz has shape of a Java-style lambda:
    //  a class that
    //    1) doesn't extend any other class;
    //    2) directly implements only one Java SAM.
    if (clazz.superType == kotlin.factory.objectType && clazz.interfaces.size() == 1) {
      return Flavour.JavaStyleLambda;
    }
    return Flavour.Unclassified;
  }
}
