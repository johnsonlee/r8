// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.verticalclassmerging;

import static com.android.tools.r8.ir.conversion.ExtraUnusedParameter.computeExtraUnusedParameters;
import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.classmerging.ClassMergerGraphLens;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.MethodLookupResult;
import com.android.tools.r8.graph.proto.ArgumentInfoCollection;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.KeepInfoCollection;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneRepresentativeHashMap;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneRepresentativeMap;
import com.android.tools.r8.utils.collections.BidirectionalOneToOneHashMap;
import com.android.tools.r8.utils.collections.BidirectionalOneToOneMap;
import com.android.tools.r8.utils.collections.MutableBidirectionalManyToOneRepresentativeMap;
import com.android.tools.r8.utils.collections.MutableBidirectionalOneToOneMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

// This graph lens is instantiated during vertical class merging. The graph lens is context
// sensitive in the enclosing class of a given invoke *and* the type of the invoke (e.g., invoke-
// super vs invoke-virtual). This is illustrated by the following example.
//
// public class A {
//   public void m() { ... }
// }
// public class B extends A {
//   @Override
//   public void m() { invoke-super A.m(); ... }
//
//   public void m2() { invoke-virtual A.m(); ... }
// }
//
// Vertical class merging will merge class A into class B. Since class B already has a method with
// the signature "void B.m()", the method A.m will be given a fresh name and moved to class B.
// During this process, the method corresponding to A.m will be made private such that it can be
// called via an invoke-direct instruction.
//
// For the invocation "invoke-super A.m()" in B.m, this graph lens will return the newly created,
// private method corresponding to A.m (that is now in B.m with a fresh name), such that the
// invocation will hit the same implementation as the original super.m() call.
//
// For the invocation "invoke-virtual A.m()" in B.m2, this graph lens will return the method B.m.
public class VerticalClassMergerGraphLens extends ClassMergerGraphLens {

  private final VerticallyMergedClasses mergedClasses;
  private final Map<DexType, Map<DexMethod, DexMethod>> contextualSuperToImplementationInContexts;
  private final BidirectionalOneToOneMap<DexMethod, DexMethod> extraNewMethodSignatures;

  private final Set<DexMethod> staticizedMethods;

  private VerticalClassMergerGraphLens(
      AppView<?> appView,
      VerticallyMergedClasses mergedClasses,
      BidirectionalManyToOneRepresentativeMap<DexField, DexField> fieldMap,
      Map<DexType, Map<DexMethod, DexMethod>> contextualSuperToImplementationInContexts,
      BidirectionalManyToOneRepresentativeMap<DexMethod, DexMethod> newMethodSignatures,
      BidirectionalOneToOneMap<DexMethod, DexMethod> extraNewMethodSignatures,
      Set<DexMethod> staticizedMethods) {
    super(
        appView,
        fieldMap,
        Collections.emptyMap(),
        mergedClasses.getBidirectionalMap(),
        newMethodSignatures);
    this.mergedClasses = mergedClasses;
    this.contextualSuperToImplementationInContexts = contextualSuperToImplementationInContexts;
    this.extraNewMethodSignatures = extraNewMethodSignatures;
    this.staticizedMethods = staticizedMethods;
  }

  public boolean hasBeenMerged(DexType type) {
    return mergedClasses.hasBeenMergedIntoSubtype(type);
  }

  public boolean hasInterfaceBeenMergedIntoClass(DexType type) {
    return mergedClasses.hasInterfaceBeenMergedIntoClass(type);
  }

  private boolean isMerged(DexMethod method) {
    DexMethod previousMethod = getPreviousMethodSignature(method);
    return mergedClasses.hasBeenMergedIntoSubtype(previousMethod.getHolderType());
  }

  private boolean isStaticized(DexMethod method) {
    return staticizedMethods.contains(method);
  }

  @Override
  public boolean isVerticalClassMergerLens() {
    return true;
  }

  @Override
  public VerticalClassMergerGraphLens asVerticalClassMergerLens() {
    return this;
  }

  @Override
  public DexType getPreviousClassType(DexType type) {
    return type;
  }

  @Override
  public DexField getNextFieldSignature(DexField previous) {
    DexField field = super.getNextFieldSignature(previous);
    assert field.verifyReferencedBaseTypesMatches(
        type -> !mergedClasses.isMergeSource(type), dexItemFactory());
    return field;
  }

  @Override
  public DexMethod getNextMethodSignature(DexMethod previous) {
    if (extraNewMethodSignatures.containsKey(previous)) {
      return getNextImplementationMethodSignature(previous);
    }
    DexMethod method = super.getNextMethodSignature(previous);
    assert method.verifyReferencedBaseTypesMatches(
        type -> !mergedClasses.isMergeSource(type), dexItemFactory());
    return method;
  }

  public DexMethod getNextBridgeMethodSignature(DexMethod previous) {
    DexMethod method = newMethodSignatures.getRepresentativeValueOrDefault(previous, previous);
    assert method.verifyReferencedBaseTypesMatches(
        type -> !mergedClasses.isMergeSource(type), dexItemFactory());
    return method;
  }

  public DexMethod getNextImplementationMethodSignature(DexMethod previous) {
    DexMethod method = extraNewMethodSignatures.getRepresentativeValueOrDefault(previous, previous);
    assert method.verifyReferencedBaseTypesMatches(
        type -> !mergedClasses.isMergeSource(type), dexItemFactory());
    return method;
  }

  @Override
  protected Iterable<DexType> internalGetOriginalTypes(DexType previous) {
    Collection<DexType> originalTypes = mergedClasses.getSourcesFor(previous);
    Iterable<DexType> currentType = IterableUtils.singleton(previous);
    if (originalTypes == null) {
      return currentType;
    }
    return Iterables.concat(currentType, originalTypes);
  }

  @Override
  protected MethodLookupResult internalLookupMethod(
      DexMethod reference,
      DexMethod context,
      InvokeType type,
      GraphLens codeLens,
      LookupMethodContinuation continuation) {
    if (this == codeLens) {
      MethodLookupResult lookupResult =
          MethodLookupResult.builder(this, codeLens)
              .setReboundReference(reference)
              .setReference(reference)
              .setType(type)
              .build();
      return continuation.lookupMethod(lookupResult);
    }
    return super.internalLookupMethod(reference, context, type, codeLens, continuation);
  }

  @Override
  public MethodLookupResult internalDescribeLookupMethod(
      MethodLookupResult previous, DexMethod context, GraphLens codeLens) {
    assert context != null || verifyIsContextFreeForMethod(previous.getReference(), codeLens);
    assert context == null || previous.getType() != null;
    assert previous.hasReboundReference();
    MethodLookupResult lookupResult;
    DexMethod implementationReference = getImplementationTargetForInvokeSuper(previous, context);
    if (implementationReference != null) {
      lookupResult =
          MethodLookupResult.builder(this, codeLens)
              .setReboundReference(implementationReference)
              .setReference(implementationReference)
              .setPrototypeChanges(
                  internalDescribePrototypeChanges(
                      previous.getPrototypeChanges(),
                      previous.getReboundReference(),
                      implementationReference))
              .setType(
                  isStaticized(implementationReference) ? InvokeType.STATIC : InvokeType.VIRTUAL)
              .build();
    } else {
      DexMethod newReboundReference = previous.getRewrittenReboundReference(newMethodSignatures);
      assert !appView.testing().enableVerticalClassMergerLensAssertion
          || newReboundReference.verifyReferencedBaseTypesMatches(
              type -> !mergedClasses.isMergeSource(type), dexItemFactory());
      DexMethod newReference =
          previous.getRewrittenReferenceFromRewrittenReboundReference(
              newReboundReference, this::getNextClassType, dexItemFactory());
      lookupResult =
          MethodLookupResult.builder(this, codeLens)
              .setReboundReference(newReboundReference)
              .setReference(newReference)
              .setType(mapInvocationType(newReference, previous.getReference(), previous.getType()))
              .setPrototypeChanges(
                  internalDescribePrototypeChanges(
                      previous.getPrototypeChanges(),
                      previous.getReboundReference(),
                      newReboundReference))
              .build();
    }
    assert !appView.testing().enableVerticalClassMergerLensAssertion
        || Streams.stream(lookupResult.getReference().getReferencedBaseTypes(dexItemFactory()))
            .noneMatch(mergedClasses::hasBeenMergedIntoSubtype);
    return lookupResult;
  }

  private DexMethod getImplementationTargetForInvokeSuper(
      MethodLookupResult previous, DexMethod context) {
    if (previous.getType().isSuper() && !isMerged(context)) {
      return contextualSuperToImplementationInContexts
          .getOrDefault(context.getHolderType(), Collections.emptyMap())
          .get(previous.getReference());
    }
    return null;
  }

  @Override
  protected RewrittenPrototypeDescription internalDescribePrototypeChanges(
      RewrittenPrototypeDescription prototypeChanges,
      DexMethod previousMethod,
      DexMethod newMethod) {
    if (isStaticized(newMethod)) {
      // The receiver has been added as an explicit argument.
      assert newMethod.getArity() == previousMethod.getArity() + 1;
      RewrittenPrototypeDescription isConvertedToStaticMethod =
          RewrittenPrototypeDescription.createForArgumentsInfo(
              ArgumentInfoCollection.builder()
                  .setArgumentInfosSize(newMethod.getParameters().size())
                  .setIsConvertedToStaticMethod()
                  .build());
      return prototypeChanges.combine(isConvertedToStaticMethod);
    }
    if (newMethod.getArity() > previousMethod.getArity()) {
      assert dexItemFactory().isConstructor(previousMethod);
      RewrittenPrototypeDescription collisionResolution =
          RewrittenPrototypeDescription.createForExtraParameters(
              computeExtraUnusedParameters(previousMethod, newMethod));
      return prototypeChanges.combine(collisionResolution);
    }
    assert newMethod.getArity() == previousMethod.getArity();
    return prototypeChanges;
  }

  @Override
  public DexMethod getPreviousMethodSignature(DexMethod method) {
    return super.getPreviousMethodSignature(
        extraNewMethodSignatures.getRepresentativeKeyOrDefault(method, method));
  }

  @Override
  public DexMethod getPreviousMethodSignatureForMapping(DexMethod method) {
    DexMethod orDefault = newMethodSignatures.getRepresentativeKeyOrDefault(method, method);
    return super.getPreviousMethodSignature(orDefault);
  }

  @Override
  protected InvokeType mapInvocationType(
      DexMethod newMethod, DexMethod previousMethod, InvokeType type) {
    if (isStaticized(newMethod)) {
      return InvokeType.STATIC;
    }
    if (type.isInterface()
        && mergedClasses.hasInterfaceBeenMergedIntoClass(previousMethod.getHolderType())) {
      DexClass newMethodHolder = appView.definitionForHolder(newMethod);
      if (newMethodHolder != null && !newMethodHolder.isInterface()) {
        return InvokeType.VIRTUAL;
      }
    }
    return type;
  }

  @Override
  public boolean isContextFreeForMethods(GraphLens codeLens) {
    if (codeLens == this) {
      return true;
    }
    return contextualSuperToImplementationInContexts.isEmpty()
        && getPrevious().isContextFreeForMethods(codeLens);
  }

  @Override
  public boolean verifyIsContextFreeForMethod(DexMethod method, GraphLens codeLens) {
    if (codeLens == this) {
      return true;
    }
    assert getPrevious().verifyIsContextFreeForMethod(method, codeLens);
    DexMethod previous = getPrevious().lookupMethod(method, null, null, codeLens).getReference();
    assert contextualSuperToImplementationInContexts.values().stream()
        .noneMatch(virtualToDirectMethodMap -> virtualToDirectMethodMap.containsKey(previous));
    return true;
  }

  public boolean assertPinnedNotModified(AppView<AppInfoWithLiveness> appView) {
    KeepInfoCollection keepInfo = appView.getKeepInfo();
    InternalOptions options = appView.options();
    keepInfo.forEachPinnedType(this::assertReferenceNotModified, options);
    keepInfo.forEachPinnedMethod(this::assertReferenceNotModified, options);
    keepInfo.forEachPinnedField(this::assertReferenceNotModified, options);
    return true;
  }

  private void assertReferenceNotModified(DexReference reference) {
    if (reference.isDexField()) {
      DexField field = reference.asDexField();
      assert getNextFieldSignature(field).isIdenticalTo(field);
    } else if (reference.isDexMethod()) {
      DexMethod method = reference.asDexMethod();
      assert getNextMethodSignature(method).isIdenticalTo(method);
    } else {
      assert reference.isDexType();
      DexType type = reference.asDexType();
      assert getNextClassType(type).isIdenticalTo(type);
    }
  }

  public static class Builder
      extends BuilderBase<VerticalClassMergerGraphLens, VerticallyMergedClasses> {

    protected final MutableBidirectionalOneToOneMap<DexField, DexField> newFieldSignatures =
        new BidirectionalOneToOneHashMap<>();
    protected final Map<DexField, DexField> pendingNewFieldSignatureUpdates =
        new IdentityHashMap<>();

    private final Map<DexType, Map<DexMethod, DexMethod>>
        contextualSuperToImplementationInContexts = new IdentityHashMap<>();

    private final MutableBidirectionalManyToOneRepresentativeMap<DexMethod, DexMethod>
        newMethodSignatures = BidirectionalManyToOneRepresentativeHashMap.newIdentityHashMap();
    private final Map<DexMethod, DexMethod> pendingNewMethodSignatureUpdates =
        new IdentityHashMap<>();

    private final MutableBidirectionalOneToOneMap<DexMethod, DexMethod> extraNewMethodSignatures =
        new BidirectionalOneToOneHashMap<>();
    private final Map<DexMethod, DexMethod> pendingExtraNewMethodSignatureUpdates =
        new IdentityHashMap<>();

    private final Set<DexMethod> staticizedMethods = Sets.newIdentityHashSet();
    private final Map<DexMethod, DexMethod> pendingStaticizedMethodUpdates =
        new IdentityHashMap<>();

    static Builder createBuilderForFixup(VerticalClassMergerResult verticalClassMergerResult) {
      return verticalClassMergerResult.getLensBuilder();
    }

    @Override
    public void commitPendingUpdates() {
      // Commit new field signatures.
      newFieldSignatures.putAll(pendingNewFieldSignatureUpdates);
      pendingNewFieldSignatureUpdates.clear();

      // Commit new method signatures.
      Map<DexMethod, DexMethod> newMethodSignatureUpdates = new IdentityHashMap<>();
      Map<DexMethod, DexMethod> newMethodSignatureRepresentativeUpdates = new IdentityHashMap<>();
      pendingNewMethodSignatureUpdates.forEach(
          (from, to) -> {
            Set<DexMethod> originalMethodSignatures = newMethodSignatures.getKeys(from);
            if (originalMethodSignatures.isEmpty()) {
              newMethodSignatureUpdates.put(from, to);
            } else {
              for (DexMethod originalMethodSignature : originalMethodSignatures) {
                newMethodSignatureUpdates.put(originalMethodSignature, to);
              }
              if (newMethodSignatures.hasExplicitRepresentativeKey(from)) {
                assert originalMethodSignatures.size() > 1;
                newMethodSignatureRepresentativeUpdates.put(
                    to, newMethodSignatures.getRepresentativeKey(from));
              } else {
                assert originalMethodSignatures.size() == 1;
              }
            }
          });
      newMethodSignatures.removeValues(pendingNewMethodSignatureUpdates.keySet());
      newMethodSignatures.putAll(newMethodSignatureUpdates);
      newMethodSignatureRepresentativeUpdates.forEach(
          (value, representative) -> {
            assert newMethodSignatures.getKeys(value).size() > 1;
            newMethodSignatures.setRepresentative(value, representative);
          });
      pendingNewMethodSignatureUpdates.clear();

      // Commit extra new method signatures.
      extraNewMethodSignatures.putAll(pendingExtraNewMethodSignatureUpdates);
      pendingExtraNewMethodSignatureUpdates.clear();

      // Commit staticized methods.
      staticizedMethods.removeAll(pendingStaticizedMethodUpdates.keySet());
      staticizedMethods.addAll(pendingStaticizedMethodUpdates.values());
      pendingStaticizedMethodUpdates.clear();
    }

    @Override
    public void fixupField(DexField oldFieldSignature, DexField newFieldSignature) {
      DexField originalFieldSignature =
          newFieldSignatures.getKeyOrDefault(oldFieldSignature, oldFieldSignature);
      pendingNewFieldSignatureUpdates.put(originalFieldSignature, newFieldSignature);
    }

    @Override
    public void fixupMethod(DexMethod oldMethodSignature, DexMethod newMethodSignature) {
      if (extraNewMethodSignatures.containsValue(oldMethodSignature)) {
        DexMethod originalMethodSignature = extraNewMethodSignatures.getKey(oldMethodSignature);
        pendingExtraNewMethodSignatureUpdates.put(originalMethodSignature, newMethodSignature);
      } else {
        pendingNewMethodSignatureUpdates.put(oldMethodSignature, newMethodSignature);
      }

      if (staticizedMethods.contains(oldMethodSignature)) {
        pendingStaticizedMethodUpdates.put(oldMethodSignature, newMethodSignature);
      }
    }

    public void fixupContextualVirtualToDirectMethodMaps() {
      for (Entry<DexType, Map<DexMethod, DexMethod>> entry :
          contextualSuperToImplementationInContexts.entrySet()) {
        for (Entry<DexMethod, DexMethod> innerEntry : entry.getValue().entrySet()) {
          DexMethod virtualMethod = innerEntry.getValue();
          DexMethod implementationMethod = extraNewMethodSignatures.get(virtualMethod);
          if (implementationMethod != null) {
            // Handle invoke-super to non-private virtual method.
            innerEntry.setValue(implementationMethod);
          } else {
            // Handle invoke-super to private virtual method (nest access).
            assert newMethodSignatures.containsKey(virtualMethod);
            innerEntry.setValue(newMethodSignatures.get(virtualMethod));
          }
        }
      }
    }

    @Override
    public Set<DexMethod> getOriginalMethodReferences(DexMethod method) {
      if (extraNewMethodSignatures.containsValue(method)) {
        return Set.of(extraNewMethodSignatures.getKey(method));
      }
      Set<DexMethod> previousMethodSignatures = newMethodSignatures.getKeys(method);
      if (!previousMethodSignatures.isEmpty()) {
        return previousMethodSignatures;
      }
      return Set.of(method);
    }

    @Override
    public VerticalClassMergerGraphLens build(
        AppView<?> appView, VerticallyMergedClasses mergedClasses) {
      // Build new graph lens.
      assert !mergedClasses.isEmpty();
      return new VerticalClassMergerGraphLens(
          appView,
          mergedClasses,
          newFieldSignatures,
          contextualSuperToImplementationInContexts,
          newMethodSignatures,
          extraNewMethodSignatures,
          staticizedMethods);
    }

    public void recordMove(DexEncodedField from, DexEncodedField to) {
      newFieldSignatures.put(from.getReference(), to.getReference());
    }

    public void recordMove(DexEncodedMethod from, DexEncodedMethod to) {
      newMethodSignatures.put(from.getReference(), to.getReference());
    }

    public void recordSplit(
        DexEncodedMethod from,
        DexEncodedMethod override,
        DexEncodedMethod bridge,
        DexEncodedMethod implementation) {
      if (override != null) {
        assert bridge == null;
        newMethodSignatures.put(from.getReference(), override.getReference());
        newMethodSignatures.put(override.getReference(), override.getReference());
        newMethodSignatures.setRepresentative(override.getReference(), override.getReference());
      } else {
        assert bridge != null;
        newMethodSignatures.put(from.getReference(), bridge.getReference());
      }

      if (implementation == null) {
        return;
      }

      extraNewMethodSignatures.put(from.getReference(), implementation.getReference());

      if (implementation.isStatic()) {
        staticizedMethods.add(implementation.getReference());
      }
    }

    public void mapVirtualMethodToDirectInType(
        DexMethod from, ProgramMethod to, DexProgramClass context) {
      contextualSuperToImplementationInContexts
          .computeIfAbsent(context.getType(), ignoreKey(IdentityHashMap::new))
          .put(from, to.getReference());
    }

    public void merge(VerticalClassMergerGraphLens.Builder builder) {
      newFieldSignatures.putAll(builder.newFieldSignatures);
      builder.newMethodSignatures.forEachManyToOneMapping(
          (keys, value, representative) -> {
            boolean isRemapping =
                Iterables.any(
                    keys,
                    key -> newMethodSignatures.containsValue(key) && key.isNotIdenticalTo(value));
            if (isRemapping) {
              // If I and J are merged into A and both I.m() and J.m() exists, then we may map J.m()
              // to I.m() as a result of merging J into A, and then subsequently merge I.m() to
              // A.m() as a result of merging I into A.
              assert keys.size() == 1;
              DexMethod key = keys.iterator().next();

              // When merging J.m() to I.m() we create the mappings {I.m(), J.m()} -> I.m().
              DexMethod originalRepresentative = newMethodSignatures.getRepresentativeKey(key);
              Set<DexMethod> originalKeys = newMethodSignatures.removeValue(key);
              assert originalKeys.contains(key);

              // Now that I.m() is merged to A.m(), we modify the existing mappings into
              // {I.m(), J.m()} -> A.m().
              newMethodSignatures.put(originalKeys, value);
              newMethodSignatures.setRepresentative(value, originalRepresentative);
            } else {
              if (newMethodSignatures.containsValue(value)
                  && !newMethodSignatures.hasExplicitRepresentativeKey(value)) {
                newMethodSignatures.setRepresentative(
                    value, newMethodSignatures.getRepresentativeKey(value));
              }
              newMethodSignatures.put(keys, value);
              if (keys.size() > 1 && !newMethodSignatures.hasExplicitRepresentativeKey(value)) {
                newMethodSignatures.setRepresentative(value, representative);
              }
            }
          });
      staticizedMethods.addAll(builder.staticizedMethods);
      extraNewMethodSignatures.putAll(builder.extraNewMethodSignatures);
      builder.contextualSuperToImplementationInContexts.forEach(
          (key, value) ->
              contextualSuperToImplementationInContexts
                  .computeIfAbsent(key, ignoreKey(IdentityHashMap::new))
                  .putAll(value));
    }
  }
}
