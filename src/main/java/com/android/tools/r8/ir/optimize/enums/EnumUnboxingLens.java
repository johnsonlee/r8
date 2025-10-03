// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums;

import static com.android.tools.r8.ir.optimize.enums.EnumUnboxerImpl.unboxedIntToOrdinal;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.MethodLookupResult;
import com.android.tools.r8.graph.lens.NestedGraphLensWithCustomLensCodeRewriter;
import com.android.tools.r8.graph.proto.ArgumentInfoCollection;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.graph.proto.RewrittenTypeInfo;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.AbstractValueFactory;
import com.android.tools.r8.ir.analysis.value.SingleFieldValue;
import com.android.tools.r8.ir.analysis.value.SingleNumberValue;
import com.android.tools.r8.ir.analysis.value.SingleValue;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.ir.conversion.ExtraUnusedParameter;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.OptionalBool;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneRepresentativeHashMap;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneRepresentativeMap;
import com.android.tools.r8.utils.collections.BidirectionalOneToManyRepresentativeHashMap;
import com.android.tools.r8.utils.collections.BidirectionalOneToManyRepresentativeMap;
import com.android.tools.r8.utils.collections.BidirectionalOneToOneHashMap;
import com.android.tools.r8.utils.collections.BidirectionalOneToOneMap;
import com.android.tools.r8.utils.collections.MutableBidirectionalManyToOneRepresentativeMap;
import com.android.tools.r8.utils.collections.MutableBidirectionalOneToManyRepresentativeMap;
import com.android.tools.r8.utils.collections.MutableBidirectionalOneToOneMap;
import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EnumUnboxingLens extends NestedGraphLensWithCustomLensCodeRewriter {

  private final AbstractValueFactory abstractValueFactory;
  private final Map<DexMethod, RewrittenPrototypeDescription> prototypeChangesPerMethod;
  private final EnumDataMap unboxedEnums;

  EnumUnboxingLens(
      AppView<?> appView,
      BidirectionalOneToOneMap<DexField, DexField> fieldMap,
      BidirectionalOneToManyRepresentativeMap<DexMethod, DexMethod> renamedSignatures,
      BidirectionalManyToOneRepresentativeMap<DexType, DexType> typeMap,
      Map<DexMethod, DexMethod> methodMap,
      Map<DexMethod, RewrittenPrototypeDescription> prototypeChangesPerMethod) {
    super(appView, fieldMap, methodMap, typeMap, renamedSignatures);
    assert !appView.unboxedEnums().isEmpty();
    this.abstractValueFactory = appView.abstractValueFactory();
    this.prototypeChangesPerMethod = prototypeChangesPerMethod;
    this.unboxedEnums = appView.unboxedEnums();
  }

  @Override
  public boolean isEnumUnboxerLens() {
    return true;
  }

  @Override
  public EnumUnboxingLens asEnumUnboxerLens() {
    return this;
  }

  public EnumDataMap getUnboxedEnums() {
    return unboxedEnums;
  }

  @Override
  public boolean isContextFreeForMethods(GraphLens codeLens) {
    if (codeLens == this) {
      return true;
    }
    return !unboxedEnums.hasAnyEnumsWithSubtypes()
        && getPrevious().isContextFreeForMethods(codeLens);
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public boolean verifyIsContextFreeForMethod(DexMethod method, GraphLens codeLens) {
    if (codeLens == this) {
      return true;
    }
    assert getPrevious().verifyIsContextFreeForMethod(getPreviousMethodSignature(method), codeLens);
    DexMethod previous =
        getPrevious()
            .lookupMethod(getPreviousMethodSignature(method), null, null, codeLens)
            .getReference();
    assert unboxedEnums.representativeType(previous.getHolderType()) == previous.getHolderType();
    return true;
  }

  public DexMethod lookupRefinedDispatchMethod(
      DexMethod method,
      AbstractValue unboxedEnumValue,
      DexType enumType) {
    DexMethod enumMethod = method.withHolder(enumType, dexItemFactory());
    DexMethod rewrittenEnumMethod = newMethodSignatures.getRepresentativeValue(enumMethod);
    if (!unboxedEnumValue.isSingleNumberValue()) {
      return rewrittenEnumMethod;
    }
    // We know the exact type of enum, so there is no need to go for the dispatch method. Instead,
    // we compute the exact target from the enum instance.
    int unboxedEnum = unboxedEnumValue.asSingleNumberValue().getIntValue();
    DexType instanceType =
        unboxedEnums
            .get(enumType)
            .valuesTypes
            .getOrDefault(unboxedIntToOrdinal(unboxedEnum), enumType);
    if (instanceType.isIdenticalTo(enumType)) {
      return rewrittenEnumMethod;
    }
    DexMethod specializedMethod = method.withHolder(instanceType, dexItemFactory());
    DexMethod refined =
        newMethodSignatures.getRepresentativeValueOrDefault(specializedMethod, rewrittenEnumMethod);
    assert refined != null;
    return refined;
  }

  @Override
  protected MethodLookupResult internalLookupMethod(
      DexMethod reference,
      DexMethod context,
      InvokeType type,
      OptionalBool isInterface,
      GraphLens codeLens,
      LookupMethodContinuation continuation) {
    if (this == codeLens) {
      // We sometimes create code objects that have the EnumUnboxingLens as code lens.
      // When using this lens as a code lens there is no lens that will insert the rebound reference
      // since the MemberRebindingIdentityLens is an ancestor of the EnumUnboxingLens.
      // We therefore use the reference itself as the rebound reference here, which is safe since
      // the code objects created during enum unboxing are guaranteed not to contain any non-rebound
      // method references.
      MethodLookupResult lookupResult =
          MethodLookupResult.builder(this, codeLens)
              .setReboundReference(reference)
              .setReference(reference)
              .setType(type)
              .build();
      return continuation.lookupMethod(lookupResult);
    }
    return super.internalLookupMethod(
        reference, context, type, isInterface, codeLens, continuation);
  }

  @Override
  public MethodLookupResult internalDescribeLookupMethod(
      MethodLookupResult previous, DexMethod context, GraphLens codeLens) {
    assert context != null || verifyIsContextFreeForMethod(previous.getReference(), codeLens);
    assert context == null || previous.getType() != null;
    DexMethod result;
    if (previous.getType() == InvokeType.SUPER) {
      assert context != null;
      DexMethod previousContext = getPreviousMethodSignature(context);
      DexType superEnum = unboxedEnums.representativeType(previousContext.getHolderType());
      if (unboxedEnums.isUnboxedEnum(superEnum)) {
        if (superEnum.isNotIdenticalTo(previousContext.getHolderType())) {
          DexMethod reference = previous.getReference();
          if (reference.getHolderType().isNotIdenticalTo(superEnum)) {
            // We are in an enum subtype where super-invokes are rebound differently.
            reference = reference.withHolder(superEnum, dexItemFactory());
          }
          result = newMethodSignatures.getRepresentativeValue(reference);
        } else {
          // This is a super-invoke to a library method, not rewritten by the lens.
          // This is rewritten by the EnumUnboxerRewriter.
          return previous.verify(this, codeLens);
        }
      } else {
        result = methodMap.apply(previous.getReference());
      }
    } else {
      result = methodMap.apply(previous.getReference());
    }
    if (result == null) {
      return previous.verify(this, codeLens);
    }
    return MethodLookupResult.builder(this, codeLens)
        .setReference(result)
        .setPrototypeChanges(
            internalDescribePrototypeChanges(
                previous.getPrototypeChanges(), previous.getReference(), result))
        .setType(mapInvocationType(result, result, previous.getReference(), previous.getType()))
        .build();
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  protected RewrittenPrototypeDescription internalDescribePrototypeChanges(
      RewrittenPrototypeDescription prototypeChanges,
      DexMethod previousMethod,
      DexMethod newMethod) {
    // Rewrite the single value of the given RewrittenPrototypeDescription if it is referring to an
    // unboxed enum field.
    if (prototypeChanges.hasRewrittenReturnInfo()) {
      RewrittenTypeInfo rewrittenReturnInfo = prototypeChanges.getRewrittenReturnInfo();
      if (rewrittenReturnInfo.hasSingleValue()) {
        SingleValue singleValue = rewrittenReturnInfo.getSingleValue();
        SingleValue rewrittenSingleValue = rewriteSingleValue(singleValue);
        if (rewrittenSingleValue != singleValue) {
          prototypeChanges =
              prototypeChanges.withRewrittenReturnInfo(
                  RewrittenTypeInfo.builder()
                      .setCastType(rewrittenReturnInfo.getCastType())
                      .setOldType(rewrittenReturnInfo.getOldType())
                      .setNewType(rewrittenReturnInfo.getNewType())
                      .setSingleValue(rewrittenSingleValue)
                      .build());
        }
      }
    }
    RewrittenPrototypeDescription enumUnboxingPrototypeChanges =
        prototypeChangesPerMethod.getOrDefault(newMethod, RewrittenPrototypeDescription.none());
    return prototypeChanges.combine(enumUnboxingPrototypeChanges);
  }

  private SingleValue rewriteSingleValue(SingleValue singleValue) {
    if (singleValue.isSingleFieldValue()) {
      SingleFieldValue singleFieldValue = singleValue.asSingleFieldValue();
      if (unboxedEnums.hasUnboxedValueFor(singleFieldValue.getField())) {
        return abstractValueFactory.createSingleNumberValue(
            unboxedEnums.getUnboxedValue(singleFieldValue.getField()), TypeElement.getInt());
      }
    }
    return singleValue;
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  protected InvokeType mapInvocationType(
      DexMethod newMethod, DexMethod newReboundMethod, DexMethod originalMethod, InvokeType type) {
    if (typeMap.containsKey(originalMethod.getHolderType())) {
      // Methods moved from unboxed enums to the utility class are either static or statified.
      assert newMethod != originalMethod;
      return InvokeType.STATIC;
    }
    return type;
  }

  public static Builder enumUnboxingLensBuilder(
      AppView<AppInfoWithLiveness> appView, EnumDataMap enumDataMap) {
    return new Builder(appView, enumDataMap);
  }

  static class Builder {

    private final DexItemFactory dexItemFactory;
    private final AbstractValueFactory abstractValueFactory;
    private final MutableBidirectionalManyToOneRepresentativeMap<DexType, DexType> typeMap =
        BidirectionalManyToOneRepresentativeHashMap.newIdentityHashMap();
    private final MutableBidirectionalOneToOneMap<DexField, DexField> newFieldSignatures =
        new BidirectionalOneToOneHashMap<>();
    private final MutableBidirectionalOneToManyRepresentativeMap<DexMethod, DexMethod>
        newMethodSignatures = new BidirectionalOneToManyRepresentativeHashMap<>();
    private final Map<DexMethod, DexMethod> methodMap = new IdentityHashMap<>();

    private final Map<DexMethod, RewrittenPrototypeDescription> prototypeChangesPerMethod =
        new IdentityHashMap<>();

    private final EnumDataMap enumDataMap;

    Builder(AppView<AppInfoWithLiveness> appView, EnumDataMap enumDataMap) {
      this.dexItemFactory = appView.dexItemFactory();
      this.abstractValueFactory = appView.abstractValueFactory();
      this.enumDataMap = enumDataMap;
    }

    public Builder mapUnboxedEnums(Set<DexType> enumsToUnbox) {
      for (DexType enumToUnbox : enumsToUnbox) {
        typeMap.put(enumToUnbox, dexItemFactory.intType);
      }
      return this;
    }

    @SuppressWarnings("ReferenceEquality")
    public void move(DexField from, DexField to) {
      if (from == to) {
        return;
      }
      synchronized (this) {
        newFieldSignatures.put(from, to);
      }
    }

    public void moveAndMap(DexMethod from, DexMethod to, boolean fromStatic) {
      moveAndMap(from, to, fromStatic, true, Collections.emptyList());
    }

    public void moveVirtual(DexMethod from, DexMethod to) {
      RewrittenPrototypeDescription prototypeChanges =
          computePrototypeChanges(from, to, false, true, false, Collections.emptyList());
      synchronized (this) {
        newMethodSignatures.put(from, to);
        prototypeChangesPerMethod.put(to, prototypeChanges);
      }
    }

    public void mapToDispatch(DexMethod from, DexMethod to) {
      RewrittenPrototypeDescription prototypeChanges =
          computePrototypeChanges(from, to, false, true, true, Collections.emptyList());
      synchronized (this) {
        methodMap.put(from, to);
        prototypeChangesPerMethod.put(to, prototypeChanges);
      }
    }

    public RewrittenPrototypeDescription moveAndMap(
        DexMethod from,
        DexMethod to,
        boolean fromStatic,
        boolean toStatic,
        List<ExtraUnusedParameter> extraUnusedParameters) {
      RewrittenPrototypeDescription prototypeChanges =
          computePrototypeChanges(from, to, fromStatic, toStatic, false, extraUnusedParameters);
      synchronized (this) {
        newMethodSignatures.put(from, to);
        methodMap.put(from, to);
        prototypeChangesPerMethod.put(to, prototypeChanges);
      }
      return prototypeChanges;
    }

    @SuppressWarnings("ReferenceEquality")
    private RewrittenPrototypeDescription computePrototypeChanges(
        DexMethod from,
        DexMethod to,
        boolean fromStatic,
        boolean toStatic,
        boolean virtualReceiverAlreadyRemapped,
        List<ExtraUnusedParameter> extraUnusedParameters) {
      assert from != to;
      int offsetDiff = 0;
      int toOffset = BooleanUtils.intValue(!toStatic);
      ArgumentInfoCollection.Builder builder =
          ArgumentInfoCollection.builder()
              .setArgumentInfosSize(from.getNumberOfArguments(fromStatic));
      if (fromStatic != toStatic) {
        assert toStatic;
        offsetDiff = 1;
        if (!virtualReceiverAlreadyRemapped) {
          RewrittenTypeInfo.Builder typeInfoBuilder =
              RewrittenTypeInfo.builder()
                  .setOldType(from.getHolderType())
                  .setNewType(to.getParameter(0));
          SingleNumberValue singleValue =
              enumDataMap.getSingleNumberValueFromEnumType(
                  abstractValueFactory, from.getHolderType());
          if (singleValue != null) {
            typeInfoBuilder.setSingleValue(singleValue);
          }
          builder.addArgumentInfo(0, typeInfoBuilder.build()).setIsConvertedToStaticMethod();
        } else {
          assert to.getParameter(0).isIntType();
          assert !fromStatic;
          assert toStatic;
          assert from.getArity() == to.getArity() - 1;
        }
      }
      for (int i = 0; i < from.getParameters().size(); i++) {
        DexType fromType = from.getParameter(i);
        DexType toType = to.getParameter(i + offsetDiff);
        if (fromType != toType) {
          builder.addArgumentInfo(
              i + offsetDiff + toOffset,
              RewrittenTypeInfo.builder().setOldType(fromType).setNewType(toType).build());
        }
      }
      RewrittenTypeInfo returnInfo =
          from.getReturnType() == to.getReturnType()
              ? null
              : RewrittenTypeInfo.builder()
                  .setOldType(from.getReturnType())
                  .setNewType(to.getReturnType())
                  .build();
      return RewrittenPrototypeDescription.createForRewrittenTypes(returnInfo, builder.build())
          .withExtraParameters(extraUnusedParameters);
    }

    void recordCheckNotZeroMethod(
        ProgramMethod checkNotNullMethod, ProgramMethod checkNotZeroMethod) {
      DexMethod originalCheckNotNullMethodSignature =
          newMethodSignatures.getKeyOrDefault(
              checkNotNullMethod.getReference(), checkNotNullMethod.getReference());
      newMethodSignatures.put(
          originalCheckNotNullMethodSignature, checkNotNullMethod.getReference());
      newMethodSignatures.put(
          originalCheckNotNullMethodSignature, checkNotZeroMethod.getReference());
      newMethodSignatures.setRepresentative(
          originalCheckNotNullMethodSignature, checkNotNullMethod.getReference());
    }

    public EnumUnboxingLens build(AppView<AppInfoWithLiveness> appView) {
      assert !typeMap.isEmpty();
      return new EnumUnboxingLens(
          appView,
          newFieldSignatures,
          newMethodSignatures,
          typeMap,
          methodMap,
          ImmutableMap.copyOf(prototypeChangesPerMethod));
    }
  }
}
