// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.graph.TopDownClassHierarchyTraversal;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.MainDexInfo;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.MethodSignatureEquivalence;
import com.android.tools.r8.utils.Timing;
import com.google.common.base.Equivalence;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.Iterables;
import it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public class ConnectedComponentVerticalClassMerger {

  private final AppView<AppInfoWithLiveness> appView;
  private final MainDexInfo mainDexInfo;

  private Collection<DexMethod> invokes;

  // The resulting graph lens that should be used after class merging.
  private final VerticalClassMergerGraphLens.Builder lensBuilder;

  // All the bridge methods that have been synthesized during vertical class merging.
  private final List<SynthesizedBridgeCode> synthesizedBridges = new ArrayList<>();

  private final VerticallyMergedClasses.Builder verticallyMergedClassesBuilder =
      VerticallyMergedClasses.builder();

  ConnectedComponentVerticalClassMerger(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
    this.mainDexInfo = appView.appInfo().getMainDexInfo();
    this.lensBuilder = new VerticalClassMergerGraphLens.Builder(appView);
  }

  public VerticalClassMergerResult.Builder run(
      Set<DexProgramClass> connectedComponent,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo,
      Set<DexProgramClass> pinnedClasses,
      Timing timing)
      throws ExecutionException {
    // Visit the program classes in a top-down order according to the class hierarchy.
    VerticalClassMergerPolicyExecutor policyExecutor =
        new VerticalClassMergerPolicyExecutor(
            appView, pinnedClasses, verticallyMergedClassesBuilder);
    Set<DexProgramClass> mergeCandidates =
        policyExecutor.run(connectedComponent, immediateSubtypingInfo);
    List<DexProgramClass> mergeCandidatesSorted =
        ListUtils.sort(mergeCandidates, Comparator.comparing(DexProgramClass::getType));
    TopDownClassHierarchyTraversal.forProgramClasses(appView)
        .visit(
            mergeCandidatesSorted,
            clazz ->
                mergeClassIfPossible(
                    clazz, immediateSubtypingInfo, mergeCandidates, policyExecutor, timing));
    return VerticalClassMergerResult.builder(
        lensBuilder, synthesizedBridges, verticallyMergedClassesBuilder);
  }

  private void mergeClassIfPossible(
      DexProgramClass clazz,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo,
      Set<DexProgramClass> mergeCandidates,
      VerticalClassMergerPolicyExecutor policyExecutor,
      Timing timing)
      throws ExecutionException {
    if (!mergeCandidates.contains(clazz)) {
      return;
    }
    List<DexProgramClass> subclasses = immediateSubtypingInfo.getSubclasses(clazz);
    if (subclasses.size() != 1) {
      return;
    }
    DexProgramClass targetClass = ListUtils.first(subclasses);
    assert !verticallyMergedClassesBuilder.isMergeSource(targetClass);
    if (verticallyMergedClassesBuilder.isMergeTarget(clazz)) {
      return;
    }
    if (verticallyMergedClassesBuilder.isMergeTarget(targetClass)) {
      if (!policyExecutor.isStillMergeCandidate(clazz, targetClass)) {
        return;
      }
    } else {
      assert policyExecutor.isStillMergeCandidate(clazz, targetClass);
    }

    // Guard against the case where we have two methods that may get the same signature
    // if we replace types. This is rare, so we approximate and err on the safe side here.
    CollisionDetector collisionDetector =
        new CollisionDetector(
            appView,
            getInvokes(immediateSubtypingInfo, mergeCandidates),
            clazz.getType(),
            targetClass.getType());
    if (collisionDetector.mayCollide(timing)) {
      return;
    }

    // Check with main dex classes to see if we are allowed to merge.
    if (!mainDexInfo.canMerge(clazz, targetClass, appView.getSyntheticItems())) {
      return;
    }

    ClassMerger merger =
        new ClassMerger(appView, lensBuilder, verticallyMergedClassesBuilder, clazz, targetClass);
    if (merger.merge()) {
      verticallyMergedClassesBuilder.add(clazz, targetClass);
      // Commit the changes to the graph lens.
      lensBuilder.merge(merger.getRenamings());
      synthesizedBridges.addAll(merger.getSynthesizedBridges());
    }
  }

  private Collection<DexMethod> getInvokes(
      ImmediateProgramSubtypingInfo immediateSubtypingInfo, Set<DexProgramClass> mergeCandidates) {
    if (invokes == null) {
      invokes =
          new OverloadedMethodSignaturesRetriever(immediateSubtypingInfo, mergeCandidates)
              .get(mergeCandidates);
    }
    return invokes;
  }

  // Collects all potentially overloaded method signatures that reference at least one type that
  // may be the source or target of a merge operation.
  private class OverloadedMethodSignaturesRetriever {
    private final Reference2BooleanOpenHashMap<DexProto> cache =
        new Reference2BooleanOpenHashMap<>();
    private final Equivalence<DexMethod> equivalence = MethodSignatureEquivalence.get();
    private final Set<DexType> mergeeCandidates = new HashSet<>();

    public OverloadedMethodSignaturesRetriever(
        ImmediateProgramSubtypingInfo immediateSubtypingInfo,
        Set<DexProgramClass> mergeCandidates) {
      for (DexProgramClass mergeCandidate : mergeCandidates) {
        List<DexProgramClass> subclasses = immediateSubtypingInfo.getSubclasses(mergeCandidate);
        if (subclasses.size() == 1) {
          mergeeCandidates.add(ListUtils.first(subclasses).getType());
        }
      }
    }

    public Collection<DexMethod> get(Set<DexProgramClass> mergeCandidates) {
      Map<DexString, DexProto> overloadingInfo = new HashMap<>();

      // Find all signatures that may reference a type that could be the source or target of a
      // merge operation.
      Set<Wrapper<DexMethod>> filteredSignatures = new HashSet<>();
      for (DexProgramClass clazz : appView.appInfo().classes()) {
        for (DexEncodedMethod encodedMethod : clazz.methods()) {
          DexMethod method = encodedMethod.getReference();
          DexClass definition = appView.definitionFor(method.getHolderType());
          if (definition != null
              && definition.isProgramClass()
              && protoMayReferenceMergedSourceOrTarget(method.getProto(), mergeCandidates)) {
            filteredSignatures.add(equivalence.wrap(method));

            // Record that we have seen a method named [signature.name] with the proto
            // [signature.proto]. If at some point, we find a method with the same name, but a
            // different proto, it could be the case that a method with the given name is
            // overloaded.
            DexProto existing =
                overloadingInfo.computeIfAbsent(method.getName(), key -> method.getProto());
            if (existing.isNotIdenticalTo(DexProto.SENTINEL)
                && !existing.equals(method.getProto())) {
              // Mark that this signature is overloaded by mapping it to SENTINEL.
              overloadingInfo.put(method.getName(), DexProto.SENTINEL);
            }
          }
        }
      }

      List<DexMethod> result = new ArrayList<>();
      for (Wrapper<DexMethod> wrappedSignature : filteredSignatures) {
        DexMethod signature = wrappedSignature.get();

        // Ignore those method names that are definitely not overloaded since they cannot lead to
        // any collisions.
        if (overloadingInfo.get(signature.getName()).isIdenticalTo(DexProto.SENTINEL)) {
          result.add(signature);
        }
      }
      return result;
    }

    private boolean protoMayReferenceMergedSourceOrTarget(
        DexProto proto, Set<DexProgramClass> mergeCandidates) {
      boolean result;
      if (cache.containsKey(proto)) {
        result = cache.getBoolean(proto);
      } else {
        result =
            Iterables.any(
                proto.getTypes(),
                type -> typeMayReferenceMergedSourceOrTarget(type, mergeCandidates));
        cache.put(proto, result);
      }
      return result;
    }

    private boolean typeMayReferenceMergedSourceOrTarget(
        DexType type, Set<DexProgramClass> mergeCandidates) {
      type = type.toBaseType(appView.dexItemFactory());
      if (type.isClassType()) {
        if (mergeeCandidates.contains(type)) {
          return true;
        }
        DexProgramClass clazz = asProgramClassOrNull(appView.definitionFor(type));
        if (clazz != null) {
          return mergeCandidates.contains(clazz.asProgramClass());
        }
      }
      return false;
    }
  }
}
