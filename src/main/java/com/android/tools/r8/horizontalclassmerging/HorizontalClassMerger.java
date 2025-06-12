// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import static com.android.tools.r8.graph.DexClassAndMethod.asProgramMethodOrNull;

import com.android.tools.r8.classmerging.ClassMergerSharedData;
import com.android.tools.r8.classmerging.Policy;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.horizontalclassmerging.code.SyntheticInitializerConverter;
import com.android.tools.r8.ir.conversion.LirConverter;
import com.android.tools.r8.naming.IdentifierMinifier;
import com.android.tools.r8.profile.art.ArtProfileCompletenessChecker;
import com.android.tools.r8.profile.rewriting.ProfileCollectionAdditions;
import com.android.tools.r8.shaking.FieldAccessInfoCollectionModifier;
import com.android.tools.r8.shaking.KeepInfoCollection;
import com.android.tools.r8.shaking.RuntimeTypeCheckInfo;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.InternalOptions.HorizontalClassMergerOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.TraversalContinuation;
import com.android.tools.r8.utils.timing.Timing;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class HorizontalClassMerger {

  private final AppView<?> appView;
  private final HorizontalClassMergerOptions options;

  private HorizontalClassMerger(AppView<?> appView) {
    this.appView = appView;
    this.options = appView.options().horizontalClassMergerOptions();
  }

  public static HorizontalClassMerger createForFinalClassMerging(
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    return new HorizontalClassMerger(appView);
  }

  public static HorizontalClassMerger createForD8ClassMerging(AppView<?> appView) {
    assert appView.options().horizontalClassMergerOptions().isRestrictedToSynthetics();
    return new HorizontalClassMerger(appView);
  }

  public void runIfNecessary(ExecutorService executorService, Timing timing)
      throws ExecutionException {
    runIfNecessary(executorService, timing, null);
  }

  public void runIfNecessary(
      ExecutorService executorService, Timing timing, RuntimeTypeCheckInfo runtimeTypeCheckInfo)
      throws ExecutionException {
    timing.begin("HorizontalClassMerger");
    if (shouldRun()) {
      run(runtimeTypeCheckInfo, executorService, timing);

      assert ArtProfileCompletenessChecker.verify(appView);

      // Clear type elements cache after IR building.
      appView.dexItemFactory().clearTypeElementsCache();
      appView.notifyOptimizationFinishedForTesting();
    } else {
      appView.setHorizontallyMergedClasses(HorizontallyMergedClasses.empty());
    }
    assert ArtProfileCompletenessChecker.verify(appView);
    timing.end();
  }

  private boolean shouldRun() {
    return options.isEnabled(appView.getWholeProgramOptimizations())
        && !appView.hasCfByteCodePassThroughMethods();
  }

  private void run(
      RuntimeTypeCheckInfo runtimeTypeCheckInfo,
      ExecutorService executorService,
      Timing timing)
      throws ExecutionException {
    // Run the policies on all program classes to produce a final grouping.
    ImmediateProgramSubtypingInfo immediateSubtypingInfo =
        appView.enableWholeProgramOptimizations()
            ? ImmediateProgramSubtypingInfo.createWithDeterministicOrder(
                appView.withClassHierarchy())
            : null;
    List<Policy> policies =
        PolicyScheduler.getPolicies(appView, immediateSubtypingInfo, runtimeTypeCheckInfo);
    Collection<HorizontalMergeGroup> groups =
        new HorizontalClassMergerPolicyExecutor()
            .run(getInitialGroups(), policies, executorService, timing);

    // If there are no groups, then end horizontal class merging.
    if (groups.isEmpty()) {
      appView.setHorizontallyMergedClasses(HorizontallyMergedClasses.empty());
      return;
    }

    ClassMergerSharedData classMergerSharedData = new ClassMergerSharedData(appView);
    HorizontalClassMergerGraphLens.Builder lensBuilder =
        new HorizontalClassMergerGraphLens.Builder();

    // Determine which classes need a class id field.
    List<ClassMerger.Builder> classMergerBuilders = createClassMergerBuilders(groups);
    initializeClassIdFields(classMergerBuilders);

    // Ensure that all allocations of classes that end up needing a class id use a constructor on
    // the class itself.
    new UndoConstructorInlining(appView, classMergerSharedData, immediateSubtypingInfo)
        .runIfNecessary(groups, executorService, timing);

    // Merge classes.
    List<ClassMerger> classMergers = createClassMergers(classMergerBuilders, lensBuilder);
    ProfileCollectionAdditions profileCollectionAdditions =
        ProfileCollectionAdditions.create(appView);
    SyntheticInitializerConverter.Builder syntheticInitializerConverterBuilder =
        SyntheticInitializerConverter.builder(appView);
    List<VirtuallyMergedMethodsKeepInfo> virtuallyMergedMethodsKeepInfos = new ArrayList<>();
    PrunedItems prunedItems =
        applyClassMergers(
            classMergers,
            classMergerSharedData,
            profileCollectionAdditions,
            syntheticInitializerConverterBuilder,
            virtuallyMergedMethodsKeepInfos::add);

    SyntheticInitializerConverter syntheticInitializerConverter =
        syntheticInitializerConverterBuilder.build();
    assert syntheticInitializerConverter.isEmpty() || appView.enableWholeProgramOptimizations();
    syntheticInitializerConverter.convertClassInitializers(executorService);

    // Generate the graph lens.
    HorizontallyMergedClasses mergedClasses =
        HorizontallyMergedClasses.builder().addMergeGroups(groups).build();
    appView.setHorizontallyMergedClasses(mergedClasses);

    HorizontalClassMergerGraphLens horizontalClassMergerGraphLens =
        createLens(
            classMergerSharedData,
            immediateSubtypingInfo,
            mergedClasses,
            lensBuilder,
            executorService,
            timing);
    profileCollectionAdditions =
        profileCollectionAdditions.rewriteMethodReferences(
            horizontalClassMergerGraphLens::getNextMethodToInvoke);

    assert verifyNoCyclesInInterfaceHierarchies(appView, groups);

    FieldAccessInfoCollectionModifier fieldAccessInfoCollectionModifier = null;
    if (appView.enableWholeProgramOptimizations()) {
      fieldAccessInfoCollectionModifier = createFieldAccessInfoCollectionModifier(groups);
    } else {
      assert groups.stream().noneMatch(HorizontalMergeGroup::hasClassIdField);
    }

    // Set the new graph lens before finalizing any synthetic code.
    appView.setGraphLens(horizontalClassMergerGraphLens);

    // Must rewrite AppInfoWithLiveness before pruning the merged classes, to ensure that allocation
    // sites, fields accesses, etc. are correctly transferred to the target classes.
    DexApplication newApplication = getNewApplication(mergedClasses);
    if (appView.enableWholeProgramOptimizations()) {
      // Finalize synthetic code.
      transformIncompleteCode(groups, horizontalClassMergerGraphLens, executorService);
      // Prune keep info.
      AppView<AppInfoWithClassHierarchy> appViewWithClassHierarchy = appView.withClassHierarchy();
      KeepInfoCollection keepInfo = appView.getKeepInfo();
      keepInfo.mutate(mutator -> mutator.removeKeepInfoForMergedClasses(prunedItems));
      appView.rewriteWithLens(horizontalClassMergerGraphLens, executorService, timing);
      new IdentifierMinifier(appViewWithClassHierarchy)
          .rewriteDexItemBasedConstStringInStaticFields(executorService);
      if (appView.options().partialSubCompilationConfiguration != null)
        appView
            .options()
            .partialSubCompilationConfiguration
            .asR8()
            .commitDexingOutputClasses(appViewWithClassHierarchy);
      LirConverter.rewriteLirWithLens(appViewWithClassHierarchy, timing, executorService);
      if (appView.options().partialSubCompilationConfiguration != null)
        appView
            .options()
            .partialSubCompilationConfiguration
            .asR8()
            .uncommitDexingOutputClasses(appViewWithClassHierarchy);
      appView.rebuildAppInfo(newApplication);
    } else {
      SyntheticItems syntheticItems = appView.appInfo().getSyntheticItems();
      assert !syntheticItems.hasPendingSyntheticClasses();
      appView
          .withoutClassHierarchy()
          .setAppInfo(
              appView
                  .appInfo()
                  .rebuildWithCommittedItems(
                      syntheticItems.commitRewrittenWithLens(
                          newApplication, horizontalClassMergerGraphLens, timing)));
      appView.rewriteWithD8Lens(horizontalClassMergerGraphLens, timing);
    }

    // Amend art profile collection.
    profileCollectionAdditions
        .setArtProfileCollection(appView.getArtProfileCollection())
        .setStartupProfile(appView.getStartupProfile())
        .commit(appView);

    // Record where the synthesized $r8$classId fields are read and written.
    if (fieldAccessInfoCollectionModifier != null) {
      fieldAccessInfoCollectionModifier.modify(appView.withLiveness());
    }

    appView.pruneItems(
        prunedItems.toBuilder().setPrunedApp(appView.app()).build(), executorService, timing);

    amendKeepInfo(horizontalClassMergerGraphLens, virtuallyMergedMethodsKeepInfos);
  }

  private void amendKeepInfo(
      HorizontalClassMergerGraphLens horizontalClassMergerGraphLens,
      List<VirtuallyMergedMethodsKeepInfo> virtuallyMergedMethodsKeepInfos) {
    if (!appView.enableWholeProgramOptimizations()) {
      assert virtuallyMergedMethodsKeepInfos.isEmpty();
      return;
    }
    appView
        .getKeepInfo()
        .mutate(
            keepInfo -> {
              for (VirtuallyMergedMethodsKeepInfo virtuallyMergedMethodsKeepInfo :
                  virtuallyMergedMethodsKeepInfos) {
                DexMethod representative = virtuallyMergedMethodsKeepInfo.getRepresentative();
                DexMethod mergedMethodReference =
                    horizontalClassMergerGraphLens.getNextMethodToInvoke(representative);
                ProgramMethod mergedMethod =
                    asProgramMethodOrNull(appView.definitionFor(mergedMethodReference));
                if (mergedMethod != null) {
                  keepInfo.joinMethod(
                      mergedMethod,
                      info -> info.merge(virtuallyMergedMethodsKeepInfo.getKeepInfo()));
                  continue;
                }
                assert false;
              }
            });
  }

  private FieldAccessInfoCollectionModifier createFieldAccessInfoCollectionModifier(
      Collection<HorizontalMergeGroup> groups) {
    FieldAccessInfoCollectionModifier.Builder builder =
        new FieldAccessInfoCollectionModifier.Builder();
    for (HorizontalMergeGroup group : groups) {
      if (group.hasClassIdField()) {
        DexProgramClass target = group.getTarget();
        target.forEachProgramInstanceInitializerMatching(
            definition -> definition.getCode().isHorizontalClassMergerCode(),
            method -> builder.recordFieldWrittenInContext(group.getClassIdField(), method));
      }
    }
    return builder.build();
  }

  private void transformIncompleteCode(
      Collection<HorizontalMergeGroup> groups,
      HorizontalClassMergerGraphLens horizontalClassMergerGraphLens,
      ExecutorService executorService)
      throws ExecutionException {
    if (!appView.hasClassHierarchy()) {
      assert verifyNoIncompleteCode(groups, executorService);
      return;
    }
    ThreadUtils.processItems(
        groups,
        group -> {
          DexProgramClass target = group.getTarget();
          target.forEachProgramMethodMatching(
              definition ->
                  definition.hasCode()
                      && definition.getCode().isIncompleteHorizontalClassMergerCode(),
              method -> {
                // Transform the code object to CfCode. This may return null if the code object does
                // not have support for generating CfCode. In this case, the call to toCfCode() will
                // lens rewrite the references of the code object using the lens.
                //
                // This should be changed to generate non-null LirCode always.
                IncompleteHorizontalClassMergerCode code =
                    (IncompleteHorizontalClassMergerCode) method.getDefinition().getCode();
                Code newCode =
                    code.toLirCode(
                        appView.withClassHierarchy(), method, horizontalClassMergerGraphLens);
                if (newCode != null) {
                  method.setCode(newCode, appView);
                }
              });
        },
        appView.options().getThreadingModule(),
        executorService);
  }

  private boolean verifyNoIncompleteCode(
      Collection<HorizontalMergeGroup> groups, ExecutorService executorService)
      throws ExecutionException {
    ThreadUtils.processItems(
        groups,
        group -> {
          assert !group
                  .getTarget()
                  .methods(
                      method ->
                          method.hasCode()
                              && method.getCode().isIncompleteHorizontalClassMergerCode())
                  .iterator()
                  .hasNext()
              : "Expected no incomplete code";
        },
        appView.options().getThreadingModule(),
        executorService);
    return true;
  }

  private DexApplication getNewApplication(HorizontallyMergedClasses mergedClasses) {
    // We must forcefully remove the merged classes from the application, since we won't run tree
    // shaking before writing the application.
    return appView
        .app()
        .builder()
        .removeProgramClasses(clazz -> mergedClasses.isMergeSource(clazz.getType()))
        .build();
  }

  // TODO(b/270398965): Replace LinkedList.
  @SuppressWarnings("JdkObsolete")
  private List<HorizontalMergeGroup> getInitialGroups() {
    HorizontalMergeGroup initialClassGroup = new HorizontalMergeGroup();
    HorizontalMergeGroup initialInterfaceGroup = new HorizontalMergeGroup();
    for (DexProgramClass clazz : appView.appInfo().classesWithDeterministicOrder()) {
      if (clazz.isInterface()) {
        initialInterfaceGroup.add(clazz);
      } else {
        initialClassGroup.add(clazz);
      }
    }
    List<HorizontalMergeGroup> initialGroups = new LinkedList<>();
    initialGroups.add(initialClassGroup);
    initialGroups.add(initialInterfaceGroup);
    initialGroups.removeIf(HorizontalMergeGroup::isTrivial);
    return initialGroups;
  }

  private List<ClassMerger.Builder> createClassMergerBuilders(
      Collection<HorizontalMergeGroup> groups) {
    List<ClassMerger.Builder> classMergerBuilders = new ArrayList<>(groups.size());
    for (HorizontalMergeGroup group : groups) {
      assert group.isNonTrivial();
      assert group.hasInstanceFieldMap();
      assert group.hasTarget();
      classMergerBuilders.add(new ClassMerger.Builder(appView, group));
    }
    return classMergerBuilders;
  }

  private void initializeClassIdFields(List<ClassMerger.Builder> classMergerBuilders) {
    for (ClassMerger.Builder classMergerBuilder : classMergerBuilders) {
      classMergerBuilder.initializeVirtualMethodMergers().initializeClassIdField();
    }
  }

  /**
   * Prepare horizontal class merging by determining which virtual methods and constructors need to
   * be merged and how the merging should be performed.
   */
  private List<ClassMerger> createClassMergers(
      List<ClassMerger.Builder> classMergerBuilders,
      HorizontalClassMergerGraphLens.Builder lensBuilder) {
    List<ClassMerger> classMergers = new ArrayList<>(classMergerBuilders.size());
    for (ClassMerger.Builder classMergerBuilder : classMergerBuilders) {
      classMergers.add(classMergerBuilder.build(lensBuilder));
    }
    appView.dexItemFactory().clearTypeElementsCache();
    return classMergers;
  }

  /** Merges all class groups using {@link ClassMerger}. */
  private PrunedItems applyClassMergers(
      Collection<ClassMerger> classMergers,
      ClassMergerSharedData classMergerSharedData,
      ProfileCollectionAdditions profileCollectionAdditions,
      SyntheticInitializerConverter.Builder syntheticInitializerConverterBuilder,
      Consumer<VirtuallyMergedMethodsKeepInfo> virtuallyMergedMethodsKeepInfoConsumer) {
    PrunedItems.Builder prunedItemsBuilder = PrunedItems.builder().setPrunedApp(appView.app());
    for (ClassMerger merger : classMergers) {
      merger.mergeGroup(
          classMergerSharedData,
          profileCollectionAdditions,
          prunedItemsBuilder,
          syntheticInitializerConverterBuilder,
          virtuallyMergedMethodsKeepInfoConsumer);
    }
    appView.dexItemFactory().clearTypeElementsCache();
    return prunedItemsBuilder.build();
  }

  /**
   * Fix all references to merged classes using the {@link HorizontalClassMergerTreeFixer}.
   * Construct a graph lens containing all changes performed by horizontal class merging.
   */
  private HorizontalClassMergerGraphLens createLens(
      ClassMergerSharedData classMergerSharedData,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo,
      HorizontallyMergedClasses mergedClasses,
      HorizontalClassMergerGraphLens.Builder lensBuilder,
      ExecutorService executorService,
      Timing timing)
      throws ExecutionException {
    return new HorizontalClassMergerTreeFixer(
            appView, classMergerSharedData, immediateSubtypingInfo, mergedClasses, lensBuilder)
        .run(executorService, timing);
  }

  private static boolean verifyNoCyclesInInterfaceHierarchies(
      AppView<?> appView, Collection<HorizontalMergeGroup> groups) {
    for (HorizontalMergeGroup group : groups) {
      if (group.isClassGroup()) {
        continue;
      }
      assert appView.hasClassHierarchy();
      DexProgramClass interfaceClass = group.getTarget();
      appView
          .withClassHierarchy()
          .appInfo()
          .traverseSuperTypes(
              interfaceClass,
              (superType, subclass, isInterface) -> {
                assert superType.isNotIdenticalTo(interfaceClass.getType())
                    : "Interface " + interfaceClass.getTypeName() + " inherits from itself";
                return TraversalContinuation.doContinue();
              });
    }
    return true;
  }
}
