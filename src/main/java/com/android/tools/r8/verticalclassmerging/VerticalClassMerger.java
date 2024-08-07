// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging;

import static com.android.tools.r8.graph.DexClassAndMethod.asProgramMethodOrNull;

import com.android.tools.r8.classmerging.ClassMergerMode;
import com.android.tools.r8.classmerging.ClassMergerSharedData;
import com.android.tools.r8.classmerging.Policy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.conversion.LirConverter;
import com.android.tools.r8.naming.IdentifierMinifier;
import com.android.tools.r8.optimize.argumentpropagation.utils.ProgramClassesBidirectedGraph;
import com.android.tools.r8.profile.art.ArtProfileCompletenessChecker;
import com.android.tools.r8.profile.rewriting.ProfileCollectionAdditions;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.KeepClassInfo.Joiner;
import com.android.tools.r8.shaking.KeepInfoCollection;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.Timing.TimingMerger;
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
  private final ClassMergerMode mode;
  private final InternalOptions options;

  public VerticalClassMerger(AppView<AppInfoWithLiveness> appView, ClassMergerMode mode) {
    this.appView = appView;
    this.dexItemFactory = appView.dexItemFactory();
    this.mode = mode;
    this.options = appView.options();
  }

  public static VerticalClassMerger createForInitialClassMerging(
      AppView<AppInfoWithLiveness> appView, Timing timing) {
    timing.begin("VerticalClassMerger (1/3)");
    return new VerticalClassMerger(appView, ClassMergerMode.INITIAL);
  }

  public static VerticalClassMerger createForIntermediateClassMerging(
      AppView<AppInfoWithLiveness> appView, Timing timing) {
    timing.begin("VerticalClassMerger (2/3)");
    return new VerticalClassMerger(appView, ClassMergerMode.FINAL);
  }

  public static VerticalClassMerger createForFinalClassMerging(
      AppView<AppInfoWithLiveness> appView, Timing timing) {
    timing.begin("VerticalClassMerger (3/3)");
    return new VerticalClassMerger(appView, ClassMergerMode.FINAL);
  }

  public void runIfNecessary(ExecutorService executorService, Timing timing)
      throws ExecutionException {
    if (shouldRun()) {
      run(executorService, timing);
    } else {
      appView.setVerticallyMergedClasses(VerticallyMergedClasses.empty(), mode);
    }
    assert appView.hasVerticallyMergedClasses();
    assert ArtProfileCompletenessChecker.verify(appView);
    timing.end();
  }

  private boolean shouldRun() {
    return options.getVerticalClassMergerOptions().isEnabled(mode)
        && !appView.hasCfByteCodePassThroughMethods();
  }

  private void run(ExecutorService executorService, Timing timing) throws ExecutionException {
    timing.begin("Setup");
    ImmediateProgramSubtypingInfo immediateSubtypingInfo =
        ImmediateProgramSubtypingInfo.createWithDeterministicOrder(appView);

    // Compute the disjoint class hierarchies for parallel processing.
    List<Set<DexProgramClass>> connectedComponents =
        new ProgramClassesBidirectedGraph(appView, immediateSubtypingInfo)
            .computeStronglyConnectedComponents();

    // Remove singleton class hierarchies as they are not subject to vertical class merging.
    connectedComponents.removeIf(connectedComponent -> connectedComponent.size() == 1);
    timing.end();

    // Apply class merging concurrently in disjoint class hierarchies.
    ClassMergerSharedData classMergerSharedData = new ClassMergerSharedData(appView);
    VerticalClassMergerResult verticalClassMergerResult =
        mergeClassesInConnectedComponents(
            classMergerSharedData,
            connectedComponents,
            immediateSubtypingInfo,
            executorService,
            timing);
    appView.setVerticallyMergedClasses(
        verticalClassMergerResult.getVerticallyMergedClasses(), mode);
    if (verticalClassMergerResult.isEmpty()) {
      return;
    }
    VerticalClassMergerGraphLens lens =
        runFixup(
            classMergerSharedData,
            immediateSubtypingInfo,
            verticalClassMergerResult,
            executorService,
            timing);
    assert verifyGraphLens(lens, verticalClassMergerResult);

    // Update keep info and art profiles.
    updateKeepInfoForMergedClasses(verticalClassMergerResult, timing);
    updateArtProfiles(lens, verticalClassMergerResult, timing);

    // Remove merged classes and rewrite AppView with the new lens.
    appView.rewriteWithLens(lens, executorService, timing);

    // The code must be rewritten before we remove the merged classes from the app. Otherwise we
    // can't build IR.
    rewriteCodeWithLens(executorService, timing);

    // Remove merged classes from app now that the code is fully rewritten.
    removeMergedClasses(
        verticalClassMergerResult.getVerticallyMergedClasses(), executorService, timing);

    // Convert the (incomplete) synthesized bridges to LIR.
    finalizeSynthesizedBridges(verticalClassMergerResult.getSynthesizedBridges(), timing);

    // Finally update the code lens to signal that the code is fully up to date.
    markRewrittenWithLens(executorService, timing);

    appView.dexItemFactory().clearTypeElementsCache();
    appView.notifyOptimizationFinishedForTesting();
  }

  private VerticalClassMergerResult mergeClassesInConnectedComponents(
      ClassMergerSharedData classMergerSharedData,
      List<Set<DexProgramClass>> connectedComponents,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo,
      ExecutorService executorService,
      Timing timing)
      throws ExecutionException {
    Collection<ConnectedComponentVerticalClassMerger> connectedComponentMergers =
        getConnectedComponentMergers(
            connectedComponents, immediateSubtypingInfo, executorService, timing);
    return applyConnectedComponentMergers(
        classMergerSharedData, connectedComponentMergers, executorService, timing);
  }

  private Collection<ConnectedComponentVerticalClassMerger> getConnectedComponentMergers(
      List<Set<DexProgramClass>> connectedComponents,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo,
      ExecutorService executorService,
      Timing timing)
      throws ExecutionException {
    timing.begin("Compute classes to merge");
    TimingMerger merger = timing.beginMerger("Compute classes to merge", executorService);
    List<ConnectedComponentVerticalClassMerger> connectedComponentMergers =
        new ArrayList<>(connectedComponents.size());
    Collection<Policy> policies = VerticalClassMergerPolicyScheduler.getPolicies(appView);
    Collection<Timing> timings =
        ThreadUtils.processItemsWithResults(
            connectedComponents,
            connectedComponent -> {
              Timing threadTiming = Timing.create("Compute classes to merge in component", options);
              ConnectedComponentVerticalClassMerger connectedComponentMerger =
                  new VerticalClassMergerPolicyExecutor(appView, immediateSubtypingInfo)
                      .run(connectedComponent, policies, executorService, threadTiming);
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
    timing.end();
    return connectedComponentMergers;
  }

  private VerticalClassMergerResult applyConnectedComponentMergers(
      ClassMergerSharedData classMergerSharedData,
      Collection<ConnectedComponentVerticalClassMerger> connectedComponentMergers,
      ExecutorService executorService,
      Timing timing)
      throws ExecutionException {
    timing.begin("Merge classes");
    TimingMerger merger = timing.beginMerger("Merge classes", executorService);
    VerticalClassMergerResult.Builder verticalClassMergerResult =
        VerticalClassMergerResult.builder(appView);
    Collection<Timing> timings =
        ThreadUtils.processItemsWithResults(
            connectedComponentMergers,
            connectedComponentMerger -> {
              Timing threadTiming = Timing.create("Merge classes in component", options);
              VerticalClassMergerResult.Builder verticalClassMergerComponentResult =
                  connectedComponentMerger.run(classMergerSharedData);
              verticalClassMergerResult.merge(verticalClassMergerComponentResult);
              threadTiming.end();
              return threadTiming;
            },
            appView.options().getThreadingModule(),
            executorService);
    merger.add(timings);
    merger.end();
    timing.end();
    return verticalClassMergerResult.build();
  }

  private VerticalClassMergerGraphLens runFixup(
      ClassMergerSharedData classMergerSharedData,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo,
      VerticalClassMergerResult verticalClassMergerResult,
      ExecutorService executorService,
      Timing timing)
      throws ExecutionException {
    return new VerticalClassMergerTreeFixer(
            appView, classMergerSharedData, immediateSubtypingInfo, verticalClassMergerResult)
        .run(executorService, timing);
  }

  private void rewriteCodeWithLens(ExecutorService executorService, Timing timing)
      throws ExecutionException {
    LirConverter.rewriteLirWithLens(appView, timing, executorService);
    new IdentifierMinifier(appView).rewriteDexItemBasedConstStringInStaticFields(executorService);
  }

  private void updateArtProfiles(
      VerticalClassMergerGraphLens verticalClassMergerLens,
      VerticalClassMergerResult verticalClassMergerResult,
      Timing timing) {
    // Include bridges in art profiles.
    ProfileCollectionAdditions profileCollectionAdditions =
        ProfileCollectionAdditions.create(appView);
    if (profileCollectionAdditions.isNop()) {
      return;
    }
    timing.begin("Update ART profiles");
    List<IncompleteVerticalClassMergerBridgeCode> synthesizedBridges =
        verticalClassMergerResult.getSynthesizedBridges();
    for (IncompleteVerticalClassMergerBridgeCode synthesizedBridge : synthesizedBridges) {
      profileCollectionAdditions.applyIfContextIsInProfile(
          verticalClassMergerLens.getPreviousMethodSignature(synthesizedBridge.getMethod()),
          additionsBuilder -> additionsBuilder.addRule(synthesizedBridge.getMethod()));
    }
    profileCollectionAdditions.commit(appView);
    timing.end();
  }

  private void updateKeepInfoForMergedClasses(
      VerticalClassMergerResult verticalClassMergerResult, Timing timing) {
    timing.begin("Update keep info");
    KeepInfoCollection keepInfo = appView.getKeepInfo();
    keepInfo.mutate(
        mutator -> {
          VerticallyMergedClasses verticallyMergedClasses =
              verticalClassMergerResult.getVerticallyMergedClasses();
          mutator.removeKeepInfoForMergedClasses(
              PrunedItems.builder()
                  .setRemovedClasses(verticallyMergedClasses.getSources())
                  .build());
          for (DexType target : verticallyMergedClasses.getTargets()) {
            DexProgramClass targetClass = appView.definitionFor(target).asProgramClass();
            if (appView.getSyntheticItems().isSynthetic(targetClass)) {
              mutator.ensureCompilerSynthesizedClass(targetClass);
              mutator.joinClass(targetClass, Joiner::disallowSyntheticSharing);
            }
          }
        });
    timing.end();
  }

  private void removeMergedClasses(
      VerticallyMergedClasses verticallyMergedClasses,
      ExecutorService executorService,
      Timing timing)
      throws ExecutionException {
    if (mode.isInitial()) {
      return;
    }
    timing.begin("Remove merged classes");
    DirectMappedDexApplication newApplication =
        appView
            .app()
            .asDirect()
            .builder()
            .removeProgramClasses(clazz -> verticallyMergedClasses.isMergeSource(clazz.getType()))
            .build();
    PrunedItems prunedItems =
        PrunedItems.builder()
            .addRemovedClasses(verticallyMergedClasses.getSources())
            .setPrunedApp(newApplication)
            .build();
    appView.setAppInfo(appView.appInfo().prunedCopyFrom(prunedItems, executorService, timing));
    timing.end();
  }

  private void finalizeSynthesizedBridges(
      List<IncompleteVerticalClassMergerBridgeCode> bridges,
      Timing timing) {
    timing.begin("Finalize synthesized bridges");
    KeepInfoCollection keepInfo = appView.getKeepInfo();
    for (IncompleteVerticalClassMergerBridgeCode code : bridges) {
      ProgramMethod bridge = asProgramMethodOrNull(appView.definitionFor(code.getMethod()));
      assert bridge != null;

      ProgramMethod target = asProgramMethodOrNull(appView.definitionFor(code.getTarget()));
      assert target != null;

      // Finalize code.
      bridge.setCode(code.toLirCode(appView), appView);

      // Copy keep info to newly synthesized methods.
      keepInfo.mutate(
          mutator ->
              mutator.joinMethod(bridge, info -> info.merge(appView.getKeepInfo(target).joiner())));
    }
    timing.end();
  }

  private void markRewrittenWithLens(ExecutorService executorService, Timing timing)
      throws ExecutionException {
    timing.begin("Mark rewritten with lens");
    appView.clearCodeRewritings(executorService, timing);
    timing.end();
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

    GraphLens previousLens = graphLens.getPrevious();
    VerticallyMergedClasses mergedClasses = verticalClassMergerResult.getVerticallyMergedClasses();
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      for (DexEncodedMethod encodedMethod : clazz.methods()) {
        DexMethod method = encodedMethod.getReference();
        DexMethod originalMethod = graphLens.getOriginalMethodSignature(method, previousLens);
        DexMethod renamedMethod = graphLens.getRenamedMethodSignature(originalMethod, previousLens);

        // Must be able to map back and forth.
        if (encodedMethod.hasCode()
            && encodedMethod.getCode() instanceof IncompleteVerticalClassMergerBridgeCode) {
          // For virtual methods, the vertical class merger creates two methods in the sub class
          // in order to deal with invoke-super instructions (one that is private and one that is
          // virtual). Therefore, it is not possible to go back and forth. Instead, we check that
          // the two methods map back to the same original method, and that the original method
          // can be mapped to the implementation method.
          DexMethod implementationMethod =
              ((IncompleteVerticalClassMergerBridgeCode) encodedMethod.getCode()).getTarget();
          DexMethod originalImplementationMethod =
              graphLens.getOriginalMethodSignature(implementationMethod, previousLens);
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
