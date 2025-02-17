// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import static com.android.tools.r8.ir.conversion.ExtraUnusedParameter.computeExtraUnusedParameters;

import com.android.tools.r8.classmerging.ClassMergerGraphLens;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMember;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.FieldLookupResult;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.MethodLookupResult;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.ir.conversion.ExtraConstantIntParameter;
import com.android.tools.r8.ir.conversion.ExtraParameter;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.OptionalBool;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneHashMap;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneMap;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneRepresentativeHashMap;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneRepresentativeMap;
import com.android.tools.r8.utils.collections.MutableBidirectionalManyToOneMap;
import com.android.tools.r8.utils.collections.MutableBidirectionalManyToOneRepresentativeMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HorizontalClassMergerGraphLens extends ClassMergerGraphLens {

  private final Map<DexMethod, ExtraConstantIntParameter> methodExtraParameters;
  private final HorizontallyMergedClasses mergedClasses;

  private HorizontalClassMergerGraphLens(
      AppView<?> appView,
      HorizontallyMergedClasses mergedClasses,
      Map<DexMethod, ExtraConstantIntParameter> methodExtraParameters,
      BidirectionalManyToOneRepresentativeMap<DexField, DexField> fieldMap,
      BidirectionalManyToOneMap<DexMethod, DexMethod> methodMap,
      BidirectionalManyToOneRepresentativeMap<DexMethod, DexMethod> newMethodSignatures) {
    super(
        appView,
        fieldMap,
        methodMap.getForwardMap(),
        mergedClasses.getBidirectionalMap(),
        newMethodSignatures);
    this.methodExtraParameters = methodExtraParameters;
    this.mergedClasses = mergedClasses;
  }

  DexMethod getNextMethodToInvoke(DexMethod method) {
    DexMethod nextMethod = methodMap.apply(method);
    return nextMethod != null ? nextMethod : method;
  }

  @Override
  public DexType getPreviousClassType(DexType type) {
    return type;
  }

  @Override
  public boolean isHorizontalClassMergerGraphLens() {
    return true;
  }

  @Override
  public HorizontalClassMergerGraphLens asHorizontalClassMergerGraphLens() {
    return this;
  }

  @Override
  protected Iterable<DexType> internalGetOriginalTypes(DexType previous) {
    return IterableUtils.prependSingleton(previous, mergedClasses.getSourcesFor(previous));
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
      // We sometimes create code objects that have the HorizontalClassMergerGraphLens as code lens.
      // When using this lens as a code lens there is no lens that will insert the rebound reference
      // since the MemberRebindingIdentityLens is an ancestor of the HorizontalClassMergerGraphLens.
      // We therefore use the reference itself as the rebound reference here, which is safe since
      // the code objects created during horizontal class merging are guaranteed not to contain
      // any non-rebound method references.
      // TODO(b/315284255): Actually guarantee the above!
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

  /**
   * If an overloaded constructor is requested, add the constructor id as a parameter to the
   * constructor. Otherwise return the lookup on the underlying graph lens.
   */
  @Override
  public MethodLookupResult internalDescribeLookupMethod(
      MethodLookupResult previous, DexMethod context, GraphLens codeLens) {
    if (!previous.hasReboundReference()) {
      return super.internalDescribeLookupMethod(previous, context, codeLens);
    }
    assert previous.hasReboundReference();
    return super.internalDescribeLookupMethod(previous, context, codeLens);
  }

  @Override
  protected FieldLookupResult internalDescribeLookupField(FieldLookupResult previous) {
    FieldLookupResult lookup = super.internalDescribeLookupField(previous);
    if (lookup.getReference().isIdenticalTo(previous.getReference())) {
      return lookup;
    }
    DexType newFieldType = lookup.getReference().getType();
    DexType preciseNewFieldType = getNextType(previous.getReference().getType());
    return FieldLookupResult.builder(this)
        .setReference(lookup.getReference())
        .setReboundReference(lookup.getReboundReference())
        .setReadCastType(
            preciseNewFieldType.isNotIdenticalTo(newFieldType) ? preciseNewFieldType : null)
        .setWriteCastType(previous.getRewrittenWriteCastType(this::getNextType))
        .build();
  }

  @Override
  protected RewrittenPrototypeDescription internalDescribePrototypeChanges(
      RewrittenPrototypeDescription prototypeChanges,
      DexMethod previousMethod,
      DexMethod newMethod) {
    if (newMethod.getArity() > previousMethod.getArity()) {
      assert dexItemFactory().isConstructor(previousMethod);
      RewrittenPrototypeDescription collisionResolution =
          RewrittenPrototypeDescription.createForExtraParameters(
              computeExtraUnusedParameters(previousMethod, newMethod));
      ExtraConstantIntParameter extraParameter = methodExtraParameters.get(previousMethod);
      if (extraParameter != null) {
        List<ExtraParameter> extraParameters =
            (List<ExtraParameter>) collisionResolution.getExtraParameters();
        extraParameters.set(0, extraParameter);
      }
      return prototypeChanges.combine(collisionResolution);
    }
    assert newMethod.getArity() == previousMethod.getArity();
    return prototypeChanges;
  }

  @Override
  public RewrittenPrototypeDescription lookupPrototypeChangesForMethodDefinition(
      DexMethod newMethod, GraphLens codeLens) {
    if (this == codeLens) {
      return getIdentityLens().lookupPrototypeChangesForMethodDefinition(newMethod, codeLens);
    }
    DexMethod previousMethod = getPreviousMethodSignature(newMethod);
    RewrittenPrototypeDescription lookup =
        getPrevious().lookupPrototypeChangesForMethodDefinition(previousMethod, codeLens);
    if (newMethod.getArity() > previousMethod.getArity()) {
      assert dexItemFactory().isConstructor(previousMethod);
      RewrittenPrototypeDescription collisionResolution =
          RewrittenPrototypeDescription.createForExtraParameters(
              computeExtraUnusedParameters(previousMethod, newMethod));
      return lookup.combine(collisionResolution);
    }
    return lookup;
  }

  public static class Builder
      extends BuilderBase<HorizontalClassMergerGraphLens, HorizontallyMergedClasses> {

    private final MutableBidirectionalManyToOneRepresentativeMap<DexField, DexField>
        newFieldSignatures = BidirectionalManyToOneRepresentativeHashMap.newIdentityHashMap();
    final MutableBidirectionalManyToOneMap<DexMethod, DexMethod> methodMap =
        BidirectionalManyToOneHashMap.newIdentityHashMap();
    private final MutableBidirectionalManyToOneRepresentativeMap<DexMethod, DexMethod>
        newMethodSignatures = BidirectionalManyToOneRepresentativeHashMap.newIdentityHashMap();
    private final Map<DexMethod, ExtraConstantIntParameter> methodExtraParameters =
        new IdentityHashMap<>();

    private final MutableBidirectionalManyToOneMap<DexMethod, DexMethod> pendingMethodMapUpdates =
        BidirectionalManyToOneHashMap.newIdentityHashMap();
    private final MutableBidirectionalManyToOneRepresentativeMap<DexField, DexField>
        pendingNewFieldSignatureUpdates =
            BidirectionalManyToOneRepresentativeHashMap.newIdentityHashMap();
    private final MutableBidirectionalManyToOneRepresentativeMap<DexMethod, DexMethod>
        pendingNewMethodSignatureUpdates =
            BidirectionalManyToOneRepresentativeHashMap.newIdentityHashMap();

    Builder() {}

    @Override
    public HorizontalClassMergerGraphLens build(
        AppView<?> appView, HorizontallyMergedClasses mergedClasses) {
      assert pendingMethodMapUpdates.isEmpty();
      assert pendingNewFieldSignatureUpdates.isEmpty();
      assert pendingNewMethodSignatureUpdates.isEmpty();
      assert newMethodSignatures.values().stream()
          .allMatch(
              value -> {
                assert newMethodSignatures.getKeys(value).size() == 1
                    || newMethodSignatures.hasExplicitRepresentativeKey(value);
                return true;
              });
      return new HorizontalClassMergerGraphLens(
          appView,
          mergedClasses,
          methodExtraParameters,
          newFieldSignatures,
          methodMap,
          newMethodSignatures);
    }

    @Override
    public Set<DexMethod> getOriginalMethodReferences(DexMethod method) {
      return methodMap.getKeys(method);
    }

    DexMethod getRenamedMethodSignature(DexMethod method) {
      assert newMethodSignatures.containsKey(method);
      return newMethodSignatures.get(method);
    }

    void recordNewFieldSignature(DexField oldFieldSignature, DexField newFieldSignature) {
      newFieldSignatures.put(oldFieldSignature, newFieldSignature);
    }

    void recordNewFieldSignature(
        Iterable<DexField> oldFieldSignatures,
        DexField newFieldSignature,
        DexField representative) {
      assert Streams.stream(oldFieldSignatures)
          .anyMatch(oldFieldSignature -> oldFieldSignature.isNotIdenticalTo(newFieldSignature));
      assert Streams.stream(oldFieldSignatures).noneMatch(newFieldSignatures::containsValue);
      assert Iterables.contains(oldFieldSignatures, representative);
      for (DexField oldFieldSignature : oldFieldSignatures) {
        recordNewFieldSignature(oldFieldSignature, newFieldSignature);
      }
      newFieldSignatures.setRepresentative(newFieldSignature, representative);
    }

    @Override
    public void fixupField(DexField oldFieldSignature, DexField newFieldSignature) {
      fixupOriginalMemberSignatures(
          oldFieldSignature,
          newFieldSignature,
          newFieldSignatures,
          pendingNewFieldSignatureUpdates);
    }

    void mapMethod(DexMethod oldMethodSignature, DexMethod newMethodSignature) {
      methodMap.put(oldMethodSignature, newMethodSignature);
    }

    void moveMethod(DexMethod from, DexMethod to) {
      moveMethod(from, to, false);
    }

    void moveMethod(DexMethod from, DexMethod to, boolean isRepresentative) {
      mapMethod(from, to);
      recordNewMethodSignature(from, to, isRepresentative);
    }

    void moveMethods(Iterable<ProgramMethod> methods, DexMethod to) {
      moveMethods(methods, to, null);
    }

    @SuppressWarnings("ReferenceEquality")
    void moveMethods(Iterable<ProgramMethod> methods, DexMethod to, ProgramMethod representative) {
      for (ProgramMethod from : methods) {
        boolean isRepresentative = representative != null && from == representative;
        moveMethod(from.getReference(), to, isRepresentative);
      }
    }

    void recordNewMethodSignature(DexMethod oldMethodSignature, DexMethod newMethodSignature) {
      recordNewMethodSignature(oldMethodSignature, newMethodSignature, false);
    }

    void recordNewMethodSignature(
        DexMethod oldMethodSignature, DexMethod newMethodSignature, boolean isRepresentative) {
      newMethodSignatures.put(oldMethodSignature, newMethodSignature);
      if (isRepresentative) {
        newMethodSignatures.setRepresentative(newMethodSignature, oldMethodSignature);
      }
    }

    @Override
    public void fixupMethod(DexMethod oldMethodSignature, DexMethod newMethodSignature) {
      fixupMethodMap(oldMethodSignature, newMethodSignature);
      fixupOriginalMemberSignatures(
          oldMethodSignature,
          newMethodSignature,
          newMethodSignatures,
          pendingNewMethodSignatureUpdates);
    }

    private void fixupMethodMap(DexMethod oldMethodSignature, DexMethod newMethodSignature) {
      Set<DexMethod> originalMethodSignatures = methodMap.getKeys(oldMethodSignature);
      if (originalMethodSignatures.isEmpty()) {
        pendingMethodMapUpdates.put(oldMethodSignature, newMethodSignature);
      } else {
        pendingMethodMapUpdates.put(originalMethodSignatures, newMethodSignature);
      }
    }

    private <D extends DexEncodedMember<D, R>, R extends DexMember<D, R>>
        void fixupOriginalMemberSignatures(
            R oldMemberSignature,
            R newMemberSignature,
            MutableBidirectionalManyToOneRepresentativeMap<R, R> newMemberSignatures,
            MutableBidirectionalManyToOneRepresentativeMap<R, R> pendingNewMemberSignatureUpdates) {
      Set<R> oldMemberSignatures = newMemberSignatures.getKeys(oldMemberSignature);
      if (oldMemberSignatures.isEmpty()) {
        pendingNewMemberSignatureUpdates.put(oldMemberSignature, newMemberSignature);
      } else {
        pendingNewMemberSignatureUpdates.put(oldMemberSignatures, newMemberSignature);
        R representative = newMemberSignatures.getRepresentativeKey(oldMemberSignature);
        if (representative != null) {
          pendingNewMemberSignatureUpdates.setRepresentative(newMemberSignature, representative);
        }
      }
    }

    @Override
    public void commitPendingUpdates() {
      // Commit pending method map updates.
      methodMap.removeAll(pendingMethodMapUpdates.keySet());
      pendingMethodMapUpdates.forEachManyToOneMapping(methodMap::put);
      pendingMethodMapUpdates.clear();

      // Commit pending original field and method signatures updates.
      commitPendingNewMemberSignatureUpdates(newFieldSignatures, pendingNewFieldSignatureUpdates);
      commitPendingNewMemberSignatureUpdates(newMethodSignatures, pendingNewMethodSignatureUpdates);
    }

    private <D extends DexEncodedMember<D, R>, R extends DexMember<D, R>>
        void commitPendingNewMemberSignatureUpdates(
            MutableBidirectionalManyToOneRepresentativeMap<R, R> newMemberSignatures,
            MutableBidirectionalManyToOneRepresentativeMap<R, R> pendingNewMemberSignatureUpdates) {
      newMemberSignatures.removeAll(pendingNewMemberSignatureUpdates.keySet());
      newMemberSignatures.putAll(pendingNewMemberSignatureUpdates);
      pendingNewMemberSignatureUpdates.clear();
    }

    /**
     * One way mapping from one constructor to another. This is used for synthesized constructors,
     * where many constructors are merged into a single constructor. The synthesized constructor
     * therefore does not have a unique reverse constructor.
     */
    void mapMergedConstructor(
        DexMethod from, DexMethod to, ExtraConstantIntParameter extraParameter) {
      mapMethod(from, to);
      if (extraParameter != null) {
        methodExtraParameters.put(from, extraParameter);
      }
    }
  }
}
