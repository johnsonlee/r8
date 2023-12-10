// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging;

import static com.android.tools.r8.graph.DexClassAndMethod.asProgramMethodOrNull;
import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.optimize.argumentpropagation.utils.ProgramClassesBidirectedGraph;
import com.android.tools.r8.profile.art.ArtProfileCompletenessChecker;
import com.android.tools.r8.profile.rewriting.ProfileCollectionAdditions;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.KeepInfoCollection;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.Timing.TimingMerger;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * Merges Supertypes with a single implementation into their single subtype.
 *
 * <p>A common use-case for this is to merge an interface into its single implementation.
 *
 * <p>The class merger only fixes the structure of the graph but leaves the actual instructions
 * untouched. Fixup of instructions is deferred via a {@link GraphLens} to the IR building phase.
 */
public class VerticalClassMerger {

  private final AppView<AppInfoWithLiveness> appView;
  private final DexItemFactory dexItemFactory;
  private final InternalOptions options;

  public VerticalClassMerger(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
    this.dexItemFactory = appView.dexItemFactory();
    this.options = appView.options();
  }

  // Returns a set of types that must not be merged into other types.
  private Set<DexProgramClass> getPinnedClasses() {
    Set<DexProgramClass> pinnedClasses = Sets.newIdentityHashSet();

    // For all pinned fields, also pin the type of the field (because changing the type of the field
    // implicitly changes the signature of the field). Similarly, for all pinned methods, also pin
    // the return type and the parameter types of the method.
    // TODO(b/156715504): Compute referenced-by-pinned in the keep info objects.
    List<DexReference> pinnedReferences = new ArrayList<>();
    KeepInfoCollection keepInfo = appView.getKeepInfo();
    keepInfo.forEachPinnedType(pinnedReferences::add, options);
    keepInfo.forEachPinnedMethod(pinnedReferences::add, options);
    keepInfo.forEachPinnedField(pinnedReferences::add, options);
    extractPinnedClasses(pinnedReferences, pinnedClasses);

    for (DexProgramClass clazz : appView.appInfo().classes()) {
      if (Iterables.any(clazz.methods(), method -> method.getAccessFlags().isNative())) {
        markClassAsPinned(clazz, pinnedClasses);
      }
    }

    // It is valid to have an invoke-direct instruction in a default interface method that targets
    // another default method in the same interface (see InterfaceMethodDesugaringTests.testInvoke-
    // SpecialToDefaultMethod). However, in a class, that would lead to a verification error.
    // Therefore, we disallow merging such interfaces into their subtypes.
    for (DexMethod signature : appView.appInfo().getVirtualMethodsTargetedByInvokeDirect()) {
      markTypeAsPinned(signature.getHolderType(), pinnedClasses);
    }

    // The set of targets that must remain for proper resolution error cases should not be merged.
    // TODO(b/192821424): Can be removed if handled.
    extractPinnedClasses(appView.appInfo().getFailedMethodResolutionTargets(), pinnedClasses);

    return pinnedClasses;
  }

  private <T extends DexReference> void extractPinnedClasses(
      Iterable<T> items, Set<DexProgramClass> pinnedClasses) {
    for (DexReference item : items) {
      if (item.isDexType()) {
        markTypeAsPinned(item.asDexType(), pinnedClasses);
      } else if (item.isDexField()) {
        // Pin the holder and the type of the field.
        DexField field = item.asDexField();
        markTypeAsPinned(field.getHolderType(), pinnedClasses);
        markTypeAsPinned(field.getType(), pinnedClasses);
      } else {
        assert item.isDexMethod();
        // Pin the holder, the return type and the parameter types of the method. If we were to
        // merge any of these types into their sub classes, then we would implicitly change the
        // signature of this method.
        DexMethod method = item.asDexMethod();
        markTypeAsPinned(method.getHolderType(), pinnedClasses);
        markTypeAsPinned(method.getReturnType(), pinnedClasses);
        for (DexType parameterType : method.getParameters()) {
          markTypeAsPinned(parameterType, pinnedClasses);
        }
      }
    }
  }

  private void markTypeAsPinned(DexType type, Set<DexProgramClass> pinnedClasses) {
    DexType baseType = type.toBaseType(dexItemFactory);
    if (!baseType.isClassType() || appView.appInfo().isPinnedWithDefinitionLookup(baseType)) {
      // We check for the case where the type is pinned according to appInfo.isPinned,
      // so we only need to add it here if it is not the case.
      return;
    }

    DexProgramClass clazz = asProgramClassOrNull(appView.definitionFor(baseType));
    if (clazz != null) {
      markClassAsPinned(clazz, pinnedClasses);
    }
  }

  private void markClassAsPinned(DexProgramClass clazz, Set<DexProgramClass> pinnedClasses) {
    pinnedClasses.add(clazz);
  }

  public static void runIfNecessary(
      AppView<AppInfoWithLiveness> appView, ExecutorService executorService, Timing timing)
      throws ExecutionException {
    timing.begin("VerticalClassMerger");
    if (shouldRun(appView)) {
      new VerticalClassMerger(appView).run(executorService, timing);
    } else {
      appView.setVerticallyMergedClasses(VerticallyMergedClasses.empty());
    }
    assert appView.hasVerticallyMergedClasses();
    assert ArtProfileCompletenessChecker.verify(appView);
    timing.end();
  }

  private static boolean shouldRun(AppView<AppInfoWithLiveness> appView) {
    return appView.options().getVerticalClassMergerOptions().isEnabled()
        && !appView.hasCfByteCodePassThroughMethods();
  }

  private void run(ExecutorService executorService, Timing timing) throws ExecutionException {
    timing.begin("Setup");
    ImmediateProgramSubtypingInfo immediateSubtypingInfo =
        ImmediateProgramSubtypingInfo.create(appView);

    // Compute the disjoint class hierarchies for parallel processing.
    List<Set<DexProgramClass>> connectedComponents =
        new ProgramClassesBidirectedGraph(appView, immediateSubtypingInfo)
            .computeStronglyConnectedComponents();

    // Remove singleton class hierarchies as they are not subject to vertical class merging.
    Set<DexProgramClass> singletonComponents = Sets.newIdentityHashSet();
    connectedComponents.removeIf(
        connectedComponent -> {
          if (connectedComponent.size() == 1) {
            singletonComponents.addAll(connectedComponent);
            return true;
          }
          return false;
        });
    timing.end();

    // Apply class merging concurrently in disjoint class hierarchies.
    VerticalClassMergerResult verticalClassMergerResult =
        mergeClassesInConnectedComponents(
            connectedComponents, immediateSubtypingInfo, executorService, timing);
    appView.setVerticallyMergedClasses(verticalClassMergerResult.getVerticallyMergedClasses());
    if (verticalClassMergerResult.isEmpty()) {
      return;
    }

    timing.begin("fixup");
    VerticalClassMergerGraphLens lens =
        new VerticalClassMergerTreeFixer(appView, verticalClassMergerResult).run(timing);
    updateKeepInfoForMergedClasses(verticalClassMergerResult);
    assert verifyGraphLens(lens, verticalClassMergerResult);
    updateArtProfiles(lens, verticalClassMergerResult);
    appView.rewriteWithLens(lens, executorService, timing);
    updateKeepInfoForSynthesizedBridges(verticalClassMergerResult);
    appView.notifyOptimizationFinishedForTesting();
  }

  private VerticalClassMergerResult mergeClassesInConnectedComponents(
      List<Set<DexProgramClass>> connectedComponents,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo,
      ExecutorService executorService,
      Timing timing)
      throws ExecutionException {
    Collection<ConnectedComponentVerticalClassMerger> connectedComponentMergers =
        getConnectedComponentMergers(
            connectedComponents, immediateSubtypingInfo, executorService, timing);
    return applyConnectedComponentMergers(
        connectedComponentMergers, immediateSubtypingInfo, executorService, timing);
  }

  private Collection<ConnectedComponentVerticalClassMerger> getConnectedComponentMergers(
      List<Set<DexProgramClass>> connectedComponents,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo,
      ExecutorService executorService,
      Timing timing)
      throws ExecutionException {
    TimingMerger merger = timing.beginMerger("Compute classes to merge", executorService);
    List<ConnectedComponentVerticalClassMerger> connectedComponentMergers =
        new ArrayList<>(connectedComponents.size());
    Set<DexProgramClass> pinnedClasses = getPinnedClasses();
    Collection<Timing> timings =
        ThreadUtils.processItemsWithResults(
            connectedComponents,
            connectedComponent -> {
              Timing threadTiming = Timing.create("Compute classes to merge in component", options);
              ConnectedComponentVerticalClassMerger connectedComponentMerger =
                  new VerticalClassMergerPolicyExecutor(appView, pinnedClasses)
                      .run(connectedComponent, immediateSubtypingInfo);
              if (!connectedComponentMerger.isEmpty()) {
                synchronized (connectedComponentMergers) {
                  connectedComponentMergers.add(connectedComponentMerger);
                }
              }
              threadTiming.end();
              return threadTiming;
            },
            appView.options().getThreadingModule(),
            executorService);
    merger.add(timings);
    merger.end();
    return connectedComponentMergers;
  }

  private VerticalClassMergerResult applyConnectedComponentMergers(
      Collection<ConnectedComponentVerticalClassMerger> connectedComponentMergers,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo,
      ExecutorService executorService,
      Timing timing)
      throws ExecutionException {
    TimingMerger merger = timing.beginMerger("Merge classes", executorService);
    VerticalClassMergerResult.Builder verticalClassMergerResult =
        VerticalClassMergerResult.builder(appView);
    Collection<Timing> timings =
        ThreadUtils.processItemsWithResults(
            connectedComponentMergers,
            connectedComponentMerger -> {
              Timing threadTiming = Timing.create("Merge classes in component", options);
              VerticalClassMergerResult.Builder verticalClassMergerComponentResult =
                  connectedComponentMerger.run(immediateSubtypingInfo);
              verticalClassMergerResult.merge(verticalClassMergerComponentResult);
              threadTiming.end();
              return threadTiming;
            },
            appView.options().getThreadingModule(),
            executorService);
    merger.add(timings);
    merger.end();
    return verticalClassMergerResult.build();
  }

  private void updateArtProfiles(
      VerticalClassMergerGraphLens verticalClassMergerLens,
      VerticalClassMergerResult verticalClassMergerResult) {
    // Include bridges in art profiles.
    ProfileCollectionAdditions profileCollectionAdditions =
        ProfileCollectionAdditions.create(appView);
    if (!profileCollectionAdditions.isNop()) {
      List<SynthesizedBridgeCode> synthesizedBridges =
          verticalClassMergerResult.getSynthesizedBridges();
      for (SynthesizedBridgeCode synthesizedBridge : synthesizedBridges) {
        profileCollectionAdditions.applyIfContextIsInProfile(
            verticalClassMergerLens.getPreviousMethodSignature(synthesizedBridge.getMethod()),
            additionsBuilder -> additionsBuilder.addRule(synthesizedBridge.getMethod()));
      }
    }
    profileCollectionAdditions.commit(appView);
  }

  private void updateKeepInfoForMergedClasses(VerticalClassMergerResult verticalClassMergerResult) {
    KeepInfoCollection keepInfo = appView.getKeepInfo();
    keepInfo.mutate(
        mutator -> {
          VerticallyMergedClasses verticallyMergedClasses =
              verticalClassMergerResult.getVerticallyMergedClasses();
          mutator.removeKeepInfoForMergedClasses(
              PrunedItems.builder()
                  .setRemovedClasses(verticallyMergedClasses.getSources())
                  .build());
        });
  }

  private void updateKeepInfoForSynthesizedBridges(
      VerticalClassMergerResult verticalClassMergerResult) {
    // Copy keep info to newly synthesized methods.
    KeepInfoCollection keepInfo = appView.getKeepInfo();
    keepInfo.mutate(
        mutator -> {
          List<SynthesizedBridgeCode> synthesizedBridges =
              verticalClassMergerResult.getSynthesizedBridges();
          for (SynthesizedBridgeCode synthesizedBridge : synthesizedBridges) {
            ProgramMethod bridge =
                asProgramMethodOrNull(appView.definitionFor(synthesizedBridge.getMethod()));
            ProgramMethod target =
                asProgramMethodOrNull(appView.definitionFor(synthesizedBridge.getTarget()));
            if (bridge != null && target != null) {
              mutator.joinMethod(bridge, info -> info.merge(appView.getKeepInfo(target).joiner()));
              continue;
            }
            assert false;
          }
        });
  }

  private boolean verifyGraphLens(
      VerticalClassMergerGraphLens graphLens, VerticalClassMergerResult verticalClassMergerResult) {
    // Note that the method assertReferencesNotModified() relies on getRenamedFieldSignature() and
    // getRenamedMethodSignature() instead of lookupField() and lookupMethod(). This is important
    // for this check to succeed, since it is not guaranteed that calling lookupMethod() with a
    // pinned method will return the method itself.
    //
    // Consider the following example.
    //
    //   class A {
    //     public void method() {}
    //   }
    //   class B extends A {
    //     @Override
    //     public void method() {}
    //   }
    //   class C extends B {
    //     @Override
    //     public void method() {}
    //   }
    //
    // If A.method() is pinned, then A cannot be merged into B, but B can still be merged into C.
    // Now, if there is an invoke-super instruction in C that hits B.method(), then this needs to
    // be rewritten into an invoke-direct instruction. In particular, there could be an instruction
    // `invoke-super A.method` in C. This would hit B.method(). Therefore, the graph lens records
    // that `invoke-super A.method` instructions, which are in one of the methods from C, needs to
    // be rewritten to `invoke-direct C.method$B`. This is valid even though A.method() is actually
    // pinned, because this rewriting does not affect A.method() in any way.
    assert graphLens.assertPinnedNotModified(appView);

    VerticallyMergedClasses mergedClasses = verticalClassMergerResult.getVerticallyMergedClasses();
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      for (DexEncodedMethod encodedMethod : clazz.methods()) {
        DexMethod method = encodedMethod.getReference();
        DexMethod originalMethod = graphLens.getOriginalMethodSignature(method);
        DexMethod renamedMethod = graphLens.getRenamedMethodSignature(originalMethod);

        // Must be able to map back and forth.
        if (encodedMethod.hasCode() && encodedMethod.getCode() instanceof SynthesizedBridgeCode) {
          // For virtual methods, the vertical class merger creates two methods in the sub class
          // in order to deal with invoke-super instructions (one that is private and one that is
          // virtual). Therefore, it is not possible to go back and forth. Instead, we check that
          // the two methods map back to the same original method, and that the original method
          // can be mapped to the implementation method.
          DexMethod implementationMethod =
              ((SynthesizedBridgeCode) encodedMethod.getCode()).getTarget();
          DexMethod originalImplementationMethod =
              graphLens.getOriginalMethodSignature(implementationMethod);
          assert originalMethod.isIdenticalTo(originalImplementationMethod);
          assert implementationMethod.isIdenticalTo(renamedMethod);
        } else {
          assert method.isIdenticalTo(renamedMethod);
        }

        // Verify that all types are up-to-date. After vertical class merging, there should be no
        // more references to types that have been merged into another type.
        assert Streams.stream(method.getReferencedBaseTypes(dexItemFactory))
            .noneMatch(mergedClasses::hasBeenMergedIntoSubtype);
      }
    }
    return true;
  }
}
