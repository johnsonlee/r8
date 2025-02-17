// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.lens;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.optimize.bridgehoisting.BridgeHoistingLens;
import com.android.tools.r8.utils.OptionalBool;

/**
 * Result of a method lookup in a GraphLens.
 *
 * <p>This provides the new target and invoke type to use, along with a description of the prototype
 * changes that have been made to the target method and the corresponding required changes to the
 * invoke arguments.
 */
public class MethodLookupResult extends MemberLookupResult<DexMethod> {

  private final OptionalBool isInterface;
  private final InvokeType type;
  private final RewrittenPrototypeDescription prototypeChanges;

  public MethodLookupResult(
      DexMethod reference,
      DexMethod reboundReference,
      OptionalBool isInterface,
      InvokeType type,
      RewrittenPrototypeDescription prototypeChanges) {
    super(reference, reboundReference);
    this.isInterface = isInterface;
    this.type = type;
    this.prototypeChanges = prototypeChanges;
  }

  public static Builder builder(GraphLens lens, GraphLens codeLens) {
    return new Builder(lens, codeLens);
  }

  public OptionalBool isInterface() {
    return isInterface;
  }

  public InvokeType getType() {
    return type;
  }

  public RewrittenPrototypeDescription getPrototypeChanges() {
    return prototypeChanges;
  }

  public MethodLookupResult verify(GraphLens lens, GraphLens codeLens) {
    assert getReference() != null;
    assert lens.isIdentityLens()
            || lens.isAppliedLens()
            || lens.asNonIdentityLens().isD8Lens()
            || getReference().getHolderType().isArrayType()
            || hasReboundReference()
            // TODO: Disallow the following.
            || lens.isEnumUnboxerLens()
            || lens.isNumberUnboxerLens()
            || lens instanceof BridgeHoistingLens
        : lens;
    return this;
  }

  public static class Builder extends MemberLookupResult.Builder<DexMethod, Builder> {

    private final GraphLens lens;
    private final GraphLens codeLens;

    private OptionalBool isInterface = OptionalBool.UNKNOWN;
    private RewrittenPrototypeDescription prototypeChanges = RewrittenPrototypeDescription.none();
    private InvokeType type;

    private Builder(GraphLens lens, GraphLens codeLens) {
      this.lens = lens;
      this.codeLens = codeLens;
    }

    public Builder setIsInterface(boolean isInterface) {
      return setIsInterface(OptionalBool.of(isInterface));
    }

    public Builder setIsInterface(OptionalBool isInterface) {
      this.isInterface = isInterface;
      return this;
    }

    public Builder setPrototypeChanges(RewrittenPrototypeDescription prototypeChanges) {
      this.prototypeChanges = prototypeChanges;
      return this;
    }

    public Builder setType(InvokeType type) {
      this.type = type;
      return this;
    }

    public MethodLookupResult build() {
      return new MethodLookupResult(
              reference, reboundReference, isInterface, type, prototypeChanges)
          .verify(lens, codeLens);
    }

    @Override
    public Builder self() {
      return this;
    }
  }
}
