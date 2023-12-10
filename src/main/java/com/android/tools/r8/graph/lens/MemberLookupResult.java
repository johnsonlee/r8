// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.lens;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.ObjectUtils;
import com.android.tools.r8.utils.collections.BidirectionalManyToManyRepresentativeMap;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneRepresentativeMap;
import java.util.Map;
import java.util.function.Function;

abstract class MemberLookupResult<R extends DexMember<?, R>> {

  private final R reference;
  private final R reboundReference;

  MemberLookupResult(R reference, R reboundReference) {
    this.reference = reference;
    this.reboundReference = reboundReference;
  }

  public R getReference() {
    return reference;
  }

  public R getRewrittenReference(BidirectionalManyToOneRepresentativeMap<R, R> rewritings) {
    return rewritings.getOrDefault(reference, reference);
  }

  public R getRewrittenReference(Map<R, R> rewritings) {
    return rewritings.getOrDefault(reference, reference);
  }

  @SuppressWarnings("unchecked")
  public R getRewrittenReferenceFromRewrittenReboundReference(
      R rewrittenReboundReference,
      Function<DexType, DexType> typeRewriter,
      DexItemFactory dexItemFactory) {
    R rewrittenReference =
        ObjectUtils.identical(reference, reboundReference)
            ? rewrittenReboundReference
            : (R)
                rewrittenReboundReference.withHolder(
                    typeRewriter.apply(reference.getHolderType()), dexItemFactory);
    return rewrittenReference;
  }

  public boolean hasReboundReference() {
    return reboundReference != null;
  }

  public R getReboundReference() {
    return reboundReference;
  }

  public R getRewrittenReboundReference(BidirectionalManyToManyRepresentativeMap<R, R> rewritings) {
    return rewritings.getRepresentativeValueOrDefault(reboundReference, reboundReference);
  }

  public R getRewrittenReboundReference(Map<R, R> rewritings) {
    return rewritings.getOrDefault(reboundReference, reboundReference);
  }

  public R getRewrittenReboundReference(Function<R, R> rewritings) {
    R rewrittenReboundReference = rewritings.apply(reboundReference);
    return rewrittenReboundReference != null ? rewrittenReboundReference : reboundReference;
  }

  abstract static class Builder<R extends DexMember<?, R>, Self extends Builder<R, Self>> {

    R reference;
    R reboundReference;

    public R getReference() {
      return reference;
    }

    public Self setReference(R reference) {
      this.reference = reference;
      return self();
    }

    public R getReboundReference() {
      return reboundReference;
    }

    public Self setReboundReference(R reboundReference) {
      this.reboundReference = reboundReference;
      return self();
    }

    public abstract Self self();
  }
}
