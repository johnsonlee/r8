// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import com.android.tools.r8.features.ClassToFeatureSplitMap;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import com.android.tools.r8.utils.structural.HasherWrapper;
import com.android.tools.r8.utils.structural.RepresentativeMap;
import com.google.common.hash.HashCode;
import java.util.Map;

/**
 * Base type for the definition of a synthetic item.
 *
 * <p>This class is internal to the synthetic items collection, thus package-protected.
 */
abstract class SyntheticDefinition<
    R extends SyntheticReference<R, D, C>,
    D extends SyntheticDefinition<R, D, C>,
    C extends DexClass> {

  private final SyntheticKind kind;
  private final SynthesizingContext context;

  SyntheticDefinition(SyntheticKind kind, SynthesizingContext context) {
    assert kind != null;
    assert context != null;
    this.kind = kind;
    this.context = context;
  }

  public boolean isClasspathDefinition() {
    return false;
  }

  public SyntheticClasspathDefinition asClasspathDefinition() {
    return null;
  }

  public boolean isMethodDefinition() {
    return false;
  }

  public SyntheticMethodDefinition asMethodDefinition() {
    return null;
  }

  public boolean isProgramDefinition() {
    return false;
  }

  public SyntheticProgramDefinition asProgramDefinition() {
    return null;
  }

  abstract R toReference();

  final SyntheticKind getKind() {
    return kind;
  }

  final SynthesizingContext getContext() {
    return context;
  }

  final String getPrefixForExternalSyntheticType(AppView<?> appView) {
    if (!appView.options().intermediate && context.isSyntheticInputClass() && !kind.isGlobal()) {
      // If the input class was a synthetic and the build is non-intermediate, unwind the synthetic
      // name back to the original context.
      return context.getSynthesizingContextType().toBinaryName();
    }
    return SyntheticNaming.getPrefixForExternalSyntheticType(
        getKind(), getHolder().getType(), appView);
  }

  public abstract C getHolder();

  final HashCode computeHash(RepresentativeMap<DexType> map, boolean intermediate) {
    HasherWrapper hasher = HasherWrapper.murmur3128Hasher();
    hasher.putInt(kind.getId());
    if (!getKind().isShareable()) {
      // Non-shareable synthetics should use its assumed unique type as the hash.
      getHolder().getType().hash(hasher);
      return hasher.hash();
    }
    if (intermediate) {
      // If in intermediate mode, include the *input* context type to restrict sharing.
      // This restricts sharing to only allow sharing the synthetics with the same *input* context.
      // If the synthetic is itself an input from a previous compilation it is restricted to share
      // within its own context only. The input context should not be mapped to an equivalence type.
      getContext().getSynthesizingInputContext(intermediate).hash(hasher);
    }
    hasher.putInt(context.getFeatureSplit().hashCode());
    internalComputeHash(hasher, map);
    return hasher.hash();
  }

  abstract void internalComputeHash(HasherWrapper hasher, RepresentativeMap<DexType> map);

  final boolean isEquivalentTo(
      D other,
      boolean includeContext,
      GraphLens graphLens,
      Map<DexMethod, DexMethod> methodMap,
      ClassToFeatureSplitMap classToFeatureSplitMap) {
    return compareTo(other, includeContext, graphLens, methodMap, classToFeatureSplitMap) == 0;
  }

  int compareTo(
      D other,
      boolean includeContext,
      GraphLens graphLens,
      Map<DexMethod, DexMethod> methodMap,
      ClassToFeatureSplitMap classToFeatureSplitMap) {
    {
      int order = kind.compareTo(other.getKind());
      if (order != 0) {
        return order;
      }
    }
    DexType thisType = getHolder().getType();
    DexType otherType = other.getHolder().getType();
    if (!getKind().isShareable()) {
      return thisType.compareTo(otherType);
    }
    if (includeContext) {
      int order = getContext().compareTo(other.getContext());
      if (order != 0) {
        return order;
      }
    }
    if (getContext().getFeatureSplit() != other.getContext().getFeatureSplit()) {
      int order =
          classToFeatureSplitMap.compareFeatureSplits(
              context.getFeatureSplit(), other.getContext().getFeatureSplit());
      assert order != 0;
      return order;
    }
    RepresentativeMap<DexType> map = null;
    // If the synthetics have been moved include the original types in the equivalence.
    if (graphLens.isNonIdentityLens()) {
      DexType thisOrigType = graphLens.getOriginalType(thisType);
      DexType otherOrigType = graphLens.getOriginalType(otherType);
      if (thisType.isNotIdenticalTo(thisOrigType) || otherType.isNotIdenticalTo(otherOrigType)) {
        map =
            t -> {
              if (DexType.identical(t, otherType)
                  || DexType.identical(t, thisOrigType)
                  || DexType.identical(t, otherOrigType)) {
                return thisType;
              }
              return t;
            };
      }
    }
    if (map == null) {
      map = t -> otherType.isIdenticalTo(t) ? thisType : t;
    }
    return internalCompareTo(other, map, m -> methodMap.getOrDefault(m, m));
  }

  abstract int internalCompareTo(
      D other, RepresentativeMap<DexType> typeMap, RepresentativeMap<DexMethod> methodMap);

  public abstract boolean isValid();
}
