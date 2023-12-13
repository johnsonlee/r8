// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.numberunboxer;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.NestedGraphLensWithCustomLensCodeRewriter;
import com.android.tools.r8.graph.proto.ArgumentInfoCollection;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.graph.proto.RewrittenTypeInfo;
import com.android.tools.r8.ir.conversion.ExtraUnusedNullParameter;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneRepresentativeMap;
import com.android.tools.r8.utils.collections.BidirectionalOneToOneHashMap;
import com.android.tools.r8.utils.collections.MutableBidirectionalOneToOneMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class NumberUnboxerLens extends NestedGraphLensWithCustomLensCodeRewriter {
  private final Map<DexMethod, RewrittenPrototypeDescription> prototypeChangesPerMethod;

  NumberUnboxerLens(
      AppView<AppInfoWithLiveness> appView,
      BidirectionalManyToOneRepresentativeMap<DexMethod, DexMethod> renamedSignatures,
      Map<DexMethod, RewrittenPrototypeDescription> prototypeChangesPerMethod,
      NumberUnboxerRewriter numberUnboxerRewriter) {
    super(appView, EMPTY_FIELD_MAP, renamedSignatures, EMPTY_TYPE_MAP, numberUnboxerRewriter);
    this.prototypeChangesPerMethod = prototypeChangesPerMethod;
  }

  @Override
  protected RewrittenPrototypeDescription internalDescribePrototypeChanges(
      RewrittenPrototypeDescription previousPrototypeChanges,
      DexMethod previousMethod,
      DexMethod newMethod) {
    RewrittenPrototypeDescription prototypeChanges =
        prototypeChangesPerMethod.getOrDefault(newMethod, RewrittenPrototypeDescription.none());
    return previousPrototypeChanges.combine(prototypeChanges);
  }

  @Override
  public boolean isNumberUnboxerLens() {
    return true;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private final Map<DexMethod, RewrittenPrototypeDescription> prototypeChangesPerMethod =
        new IdentityHashMap<>();
    private final MutableBidirectionalOneToOneMap<DexMethod, DexMethod> newMethodSignatures =
        new BidirectionalOneToOneHashMap<>();

    public RewrittenPrototypeDescription move(DexEncodedMethod fromEncoded, DexMethod to) {
      DexMethod from = fromEncoded.getReference();
      assert !from.isIdenticalTo(to);
      List<ExtraUnusedNullParameter> extraUnusedNullParameters =
          ExtraUnusedNullParameter.computeExtraUnusedNullParameters(from, to);
      RewrittenPrototypeDescription prototypeChanges =
          computePrototypeChanges(from, to, fromEncoded.isStatic(), extraUnusedNullParameters);
      synchronized (this) {
        newMethodSignatures.put(from, to);
        prototypeChangesPerMethod.put(to, prototypeChanges);
      }
      return prototypeChanges;
    }

    private RewrittenPrototypeDescription computePrototypeChanges(
        DexMethod from,
        DexMethod to,
        boolean staticMethod,
        List<ExtraUnusedNullParameter> extraUnusedNullParameters) {
      assert from.getArity() + extraUnusedNullParameters.size() == to.getArity();
      ArgumentInfoCollection.Builder builder =
          ArgumentInfoCollection.builder()
              .setArgumentInfosSize(from.getNumberOfArguments(staticMethod));
      int shift = BooleanUtils.intValue(!staticMethod);
      for (int i = 0; i < from.getParameters().size(); i++) {
        DexType fromType = from.getParameter(i);
        DexType toType = to.getParameter(i);
        if (!fromType.isIdenticalTo(toType)) {
          builder.addArgumentInfo(
              shift + i,
              RewrittenTypeInfo.builder().setOldType(fromType).setNewType(toType).build());
        }
      }
      RewrittenTypeInfo returnInfo =
          from.getReturnType().isIdenticalTo(to.getReturnType())
              ? null
              : RewrittenTypeInfo.builder()
                  .setOldType(from.getReturnType())
                  .setNewType(to.getReturnType())
                  .build();
      return RewrittenPrototypeDescription.create(
          extraUnusedNullParameters, returnInfo, builder.build());
    }

    public NumberUnboxerLens build(
        AppView<AppInfoWithLiveness> appView, NumberUnboxerRewriter numberUnboxerRewriter) {
      return new NumberUnboxerLens(
          appView, newMethodSignatures, prototypeChangesPerMethod, numberUnboxerRewriter);
    }
  }
}
