// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.lens.DefaultNonIdentityGraphLens;
import com.android.tools.r8.graph.lens.FieldLookupResult;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.GraphLensUtils;
import com.android.tools.r8.graph.lens.MethodLookupResult;
import com.android.tools.r8.graph.lens.NonIdentityGraphLens;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This lens is used to populate the rebound field and method reference during lookup, such that
 * both the non-rebound and rebound field and method references are available to all descendants of
 * this lens.
 */
public class MemberRebindingIdentityLens extends DefaultNonIdentityGraphLens {

  private final Map<DexField, DexField> nonReboundFieldReferenceToDefinitionMap;
  private final Map<DexMethod, DexMethod> nonReboundMethodReferenceToDefinitionMap;

  private MemberRebindingIdentityLens(
      Map<DexField, DexField> nonReboundFieldReferenceToDefinitionMap,
      Map<DexMethod, DexMethod> nonReboundMethodReferenceToDefinitionMap,
      AppView<? extends AppInfoWithClassHierarchy> appView,
      GraphLens previousLens) {
    super(appView, previousLens);
    this.nonReboundFieldReferenceToDefinitionMap = nonReboundFieldReferenceToDefinitionMap;
    this.nonReboundMethodReferenceToDefinitionMap = nonReboundMethodReferenceToDefinitionMap;
  }

  public static Builder builder(AppView<? extends AppInfoWithClassHierarchy> appView) {
    return builder(appView, appView.graphLens());
  }

  public static Builder builder(
      AppView<? extends AppInfoWithClassHierarchy> appView, GraphLens previousLens) {
    return new Builder(appView, previousLens, new IdentityHashMap<>(), new IdentityHashMap<>());
  }

  public static Builder concurrentBuilder(AppView<? extends AppInfoWithClassHierarchy> appView) {
    return concurrentBuilder(appView, appView.graphLens());
  }

  public static Builder concurrentBuilder(
      AppView<? extends AppInfoWithClassHierarchy> appView, GraphLens previousLens) {
    return new Builder(appView, previousLens, new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
  }

  public void addNonReboundMethodReference(
      DexMethod nonReboundMethodReference, DexMethod reboundMethodReference) {
    nonReboundMethodReferenceToDefinitionMap.put(nonReboundMethodReference, reboundMethodReference);
  }

  @Override
  public boolean hasCodeRewritings() {
    return false;
  }

  @Override
  protected FieldLookupResult internalDescribeLookupField(FieldLookupResult previous) {
    assert !previous.hasReadCastType();
    assert !previous.hasReboundReference();
    return FieldLookupResult.builder(this)
        .setReference(previous.getReference())
        .setReboundReference(getReboundFieldReference(previous.getReference()))
        .build();
  }

  private DexField getReboundFieldReference(DexField field) {
    return nonReboundFieldReferenceToDefinitionMap.getOrDefault(field, field);
  }

  @Override
  public MethodLookupResult internalDescribeLookupMethod(
      MethodLookupResult previous, DexMethod context, GraphLens codeLens) {
    assert previous.getReboundReference() == null;
    return MethodLookupResult.builder(this, codeLens)
        .setReference(previous.getReference())
        .setReboundReference(getReboundMethodReference(previous.getReference()))
        .setPrototypeChanges(previous.getPrototypeChanges())
        .setType(previous.getType())
        .setIsInterface(previous.isInterface())
        .build();
  }

  private DexMethod getReboundMethodReference(DexMethod method) {
    DexMethod rebound = nonReboundMethodReferenceToDefinitionMap.get(method);
    assert method.isNotIdenticalTo(rebound);
    while (rebound != null) {
      method = rebound;
      rebound = nonReboundMethodReferenceToDefinitionMap.get(method);
    }
    return method;
  }

  @Override
  public boolean isMemberRebindingIdentityLens() {
    return true;
  }

  @Override
  public MemberRebindingIdentityLens asMemberRebindingIdentityLens() {
    return this;
  }

  public MemberRebindingIdentityLens toRewrittenMemberRebindingIdentityLens(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      GraphLens lens,
      NonIdentityGraphLens appliedMemberRebindingLens) {
    return toRewrittenMemberRebindingIdentityLens(
        appView, lens, appliedMemberRebindingLens, getIdentityLens());
  }

  public MemberRebindingIdentityLens toRewrittenMemberRebindingIdentityLens(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      GraphLens lens,
      NonIdentityGraphLens appliedMemberRebindingLens,
      GraphLens newPreviousLens) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    Builder builder = builder(appView, newPreviousLens);
    nonReboundFieldReferenceToDefinitionMap.forEach(
        (nonReboundFieldReference, reboundFieldReference) -> {
          DexField rewrittenReboundFieldReference =
              lens.lookupField(reboundFieldReference, appliedMemberRebindingLens);
          DexField rewrittenNonReboundFieldReference =
              rewrittenReboundFieldReference.withHolder(
                  lens.lookupType(
                      nonReboundFieldReference.getHolderType(), appliedMemberRebindingLens),
                  dexItemFactory);
          if (rewrittenNonReboundFieldReference.isNotIdenticalTo(rewrittenReboundFieldReference)) {
            builder.recordNonReboundFieldAccess(
                rewrittenNonReboundFieldReference, rewrittenReboundFieldReference);
          }
        });

    Deque<NonIdentityGraphLens> lenses = GraphLensUtils.extractNonIdentityLenses(lens);
    nonReboundMethodReferenceToDefinitionMap.forEach(
        (nonReboundMethodReference, reboundMethodReference) -> {
          DexMethod rewrittenReboundMethodReference = reboundMethodReference;
          for (NonIdentityGraphLens currentLens : lenses) {
            if (currentLens.isVerticalClassMergerLens()) {
              // The vertical class merger lens maps merged virtual methods to private methods in
              // the subclass. Invokes to such virtual methods are mapped to the corresponding
              // virtual method in the subclass.
              rewrittenReboundMethodReference =
                  currentLens
                      .asVerticalClassMergerLens()
                      .getNextBridgeMethodSignature(rewrittenReboundMethodReference);
            } else {
              rewrittenReboundMethodReference =
                  currentLens.getNextMethodSignature(rewrittenReboundMethodReference);
            }
          }
          DexMethod rewrittenNonReboundMethodReference =
              rewrittenReboundMethodReference.withHolder(
                  lens.lookupType(
                      nonReboundMethodReference.getHolderType(), appliedMemberRebindingLens),
                  dexItemFactory);
          if (rewrittenNonReboundMethodReference.isNotIdenticalTo(
              rewrittenReboundMethodReference)) {
            builder.recordNonReboundMethodAccess(
                rewrittenNonReboundMethodReference, rewrittenReboundMethodReference);
          }
        });
    return builder.build();
  }

  public static class Builder {

    private final AppView<? extends AppInfoWithClassHierarchy> appView;
    private final GraphLens previousLens;

    private final Map<DexField, DexField> nonReboundFieldReferenceToDefinitionMap;
    private final Map<DexMethod, DexMethod> nonReboundMethodReferenceToDefinitionMap;

    private Builder(
        AppView<? extends AppInfoWithClassHierarchy> appView,
        GraphLens previousLens,
        Map<DexField, DexField> nonReboundFieldReferenceToDefinitionMap,
        Map<DexMethod, DexMethod> nonReboundMethodReferenceToDefinitionMap) {
      this.appView = appView;
      this.previousLens = previousLens;
      this.nonReboundFieldReferenceToDefinitionMap = nonReboundFieldReferenceToDefinitionMap;
      this.nonReboundMethodReferenceToDefinitionMap = nonReboundMethodReferenceToDefinitionMap;
    }

    private void recordNonReboundFieldAccess(
        DexField nonReboundFieldReference, DexField reboundFieldReference) {
      assert nonReboundFieldReference.isNotIdenticalTo(reboundFieldReference);
      nonReboundFieldReferenceToDefinitionMap.put(nonReboundFieldReference, reboundFieldReference);
    }

    private void recordNonReboundMethodAccess(
        DexMethod nonReboundMethodReference, DexMethod reboundMethodReference) {
      assert nonReboundMethodReference.isNotIdenticalTo(reboundMethodReference);
      nonReboundMethodReferenceToDefinitionMap.put(
          nonReboundMethodReference, reboundMethodReference);
    }

    void recordFieldAccess(DexField reference) {
      DexEncodedField resolvedField = appView.appInfo().resolveField(reference).getResolvedField();
      if (resolvedField != null && resolvedField.getReference().isNotIdenticalTo(reference)) {
        recordNonReboundFieldAccess(reference, resolvedField.getReference());
      }
    }

    void recordMethodAccess(DexMethod reference) {
      if (reference.getHolderType().isArrayType()) {
        return;
      }
      // TODO(b/324526473): Use normal definitionFor() when LIR constant pool does not have any
      //  outdated, unused constants.
      DexClass holder =
          appView.appInfo().definitionForWithoutExistenceAssert(reference.getHolderType());
      if (holder != null) {
        SingleResolutionResult<?> resolutionResult =
            appView.appInfo().resolveMethodOnLegacy(holder, reference).asSingleResolution();
        if (resolutionResult != null && resolutionResult.getResolvedHolder() != holder) {
          recordNonReboundMethodAccess(
              reference, resolutionResult.getResolvedMethod().getReference());
        }
      }
    }

    MemberRebindingIdentityLens build() {
      // This intentionally does not return null when the maps are empty. In this case there are no
      // non-rebound field or method references, but the member rebinding lens is still needed to
      // populate the rebound reference during field and method lookup.
      return new MemberRebindingIdentityLens(
          nonReboundFieldReferenceToDefinitionMap,
          nonReboundMethodReferenceToDefinitionMap,
          appView,
          previousLens);
    }
  }
}
