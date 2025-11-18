// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.outliner.bottomup;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;
import static com.google.common.base.Predicates.alwaysTrue;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.contexts.CompilationContext.ProcessorContext;
import com.android.tools.r8.dex.code.DexConst4;
import com.android.tools.r8.dex.code.DexConstWide16;
import com.android.tools.r8.dex.code.DexInvokeStatic;
import com.android.tools.r8.dex.code.DexReturn;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.optimize.DeadCodeRemover;
import com.android.tools.r8.lightir.LirCode;
import com.android.tools.r8.lightir.LirConstant;
import com.android.tools.r8.lightir.LirOpcodes;
import com.android.tools.r8.profile.rewriting.ProfileCollectionAdditions;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.collections.ProgramMethodMap;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class BottomUpOutliner {

  private final AppView<?> appView;
  private final BottomUpOutlinerOptions outlinerOptions;

  // Scans IR code during IR conversion. Responsible for computing candidate outlines.
  private BottomUpOutlinerScanner scanner;

  private BottomUpOutliner(AppView<?> appView) {
    this.appView = appView;
    this.outlinerOptions = appView.options().getBottomUpOutlinerOptions();
    this.scanner = new BottomUpOutlinerScanner(appView);
  }

  public static BottomUpOutliner create(AppView<?> appView) {
    return appView.options().getBottomUpOutlinerOptions().isEnabled(appView)
        ? new BottomUpOutliner(appView)
        : null;
  }

  // TODO(b/434769547): Ensure retracing works.
  public void runForR8(ExecutorService executorService, Timing timing) throws ExecutionException {
    timing.begin("Throw block outliner");
    scan(executorService, timing);
    ProfileCollectionAdditions profileCollectionAdditions =
        ProfileCollectionAdditions.create(appView);
    tearDownScanner(Collections.emptyMap(), profileCollectionAdditions, executorService);
    profileCollectionAdditions.commit(appView);
    // Commit pending synthetics.
    appView.rebuildAppInfo();
    appView.getTypeElementFactory().clearTypeElementsCache();
    timing.end();
  }

  public void scan(ExecutorService executorService, Timing timing) throws ExecutionException {
    ThreadUtils.processItemsThatMatches(
        appView.appInfo().classes(),
        alwaysTrue(),
        this::scan,
        appView.options(),
        executorService,
        timing,
        timing.beginMerger("Scan for throw block outlines", executorService));
  }

  private void scan(DexProgramClass clazz, Timing timing) {
    timing.begin("Scan " + clazz.getTypeName());
    DeadCodeRemover deadCodeRemover = new DeadCodeRemover(appView);
    clazz.forEachProgramMethodMatching(
        this::hasLirCodeMaybeSubjectToOutlining,
        method -> {
          IRCode code = method.buildIR(appView);
          scan(code);
          method.setCode(code, appView, deadCodeRemover, timing);
        });
    timing.end();
  }

  private boolean hasLirCodeMaybeSubjectToOutlining(DexEncodedMethod method) {
    if (!method.hasLirCode()) {
      return false;
    }
    LirCode<?> lirCode = method.getCode().asLirCode();
    if (Iterables.any(lirCode, instruction -> instruction.getOpcode() == LirOpcodes.ATHROW)) {
      // Maybe subject to throw outlining.
      return true;
    }
    DexType stringBuilderType = appView.dexItemFactory().stringBuilderType;
    for (LirConstant constant : lirCode.getConstantPool()) {
      if (constant instanceof DexMethod) {
        DexMethod methodConstant = (DexMethod) constant;
        if (methodConstant.getHolderType().isIdenticalTo(stringBuilderType)) {
          // Maybe subject to StringBuilder outlining.
          return true;
        }
      }
    }
    return false;
  }

  public void scan(IRCode code) {
    // Notify the scanner.
    if (scanner != null) {
      scanner.run(code);
    }
  }

  public void tearDownScanner(
      Map<DexMethod, DexMethod> forcefullyMovedLambdaMethods,
      ProfileCollectionAdditions profileCollectionAdditions,
      ExecutorService executorService)
      throws ExecutionException {
    // Unset the scanner, which is responsible for computing outline candidates.
    assert scanner != null;
    Collection<Outline> outlines = scanner.getOutlines();
    scanner = null;

    // Create outlines.
    updateOutlineUsers(outlines, forcefullyMovedLambdaMethods);
    materializeOutlines(outlines, profileCollectionAdditions, executorService);
    assert supplyOutlineConsumerForTesting(outlines);

    // Convert LIR to DEX.
    processMethods(outlines, executorService);
    appView.unsetBottomUpOutliner();
  }

  private void updateOutlineUsers(
      Collection<Outline> outlines, Map<DexMethod, DexMethod> forcefullyMovedLambdaMethods) {
    for (Outline outline : outlines) {
      outline.updateUsers(appView, forcefullyMovedLambdaMethods);
    }
  }

  private void materializeOutlines(
      Collection<Outline> outlines,
      ProfileCollectionAdditions profileCollectionAdditions,
      ExecutorService executorService)
      throws ExecutionException {
    // Find the outlines that we need to synthesize from each method.
    ProgramMethodMap<List<Outline>> synthesizingContexts = ProgramMethodMap.create();
    for (Outline outline : outlines) {
      ProgramMethod synthesizingContext = outline.getSynthesizingContext(appView);
      if (shouldMaterializeOutline(outline, synthesizingContext)) {
        synthesizingContexts
            .computeIfAbsent(synthesizingContext, ignoreKey(ArrayList::new))
            .add(outline);
      }
    }

    // Sort the outlines per synthesizing context so that the synthesis order is deterministic.
    // We use the constant pool index of the outline as sorting key.
    synthesizingContexts.forEach(
        (synthesizingContext, outlinesFromSynthesizingContext) -> {
          if (outlinesFromSynthesizingContext.size() == 1) {
            return;
          }
          LirConstant[] constantPool =
              synthesizingContext.getDefinition().getCode().asLirCode().getConstantPool();
          Reference2IntMap<Outline> outlineConstantPoolIndices = new Reference2IntOpenHashMap<>();
          for (int i = 0; i < constantPool.length; i++) {
            LirConstant constant = constantPool[i];
            if (constant instanceof Outline) {
              outlineConstantPoolIndices.put((Outline) constant, i);
            }
          }
          assert outlinesFromSynthesizingContext.stream()
              .allMatch(outlineConstantPoolIndices::containsKey);
          ListUtils.destructiveSort(
              outlinesFromSynthesizingContext,
              Comparator.comparingInt(outlineConstantPoolIndices::getInt));
        });

    // Synthesize the outlines concurrently.
    ProcessorContext processorContext = appView.createProcessorContext();
    ThreadUtils.processMap(
        synthesizingContexts,
        (synthesizingContext, outlinesFromSynthesizingContext) -> {
          MethodProcessingContext methodProcessingContext =
              processorContext.createMethodProcessingContext(synthesizingContext);
          for (Outline outline : outlinesFromSynthesizingContext) {
            ProgramMethod outlineMethod = outline.materialize(appView, methodProcessingContext);
            if (!profileCollectionAdditions.isNop()) {
              for (Multiset.Entry<DexMethod> user : outline.getUsers().entrySet()) {
                DexMethod userReference = user.getElement();
                profileCollectionAdditions.applyIfContextIsInProfile(
                    userReference,
                    additions ->
                        additions
                            .addClassRule(outlineMethod.getHolderType())
                            .addMethodRule(outlineMethod.getReference()));
              }
            }
          }
        },
        appView.options().getThreadingModule(),
        executorService);
  }

  private boolean shouldMaterializeOutline(Outline outline, ProgramMethod synthesizingContext) {
    Predicate<Outline> outlineStrategyForTesting = outlinerOptions.outlineStrategyForTesting;
    if (outlineStrategyForTesting != null) {
      return outlineStrategyForTesting.test(outline);
    }
    if (outline.getNumberOfUsers() >= outlinerOptions.forceUsers) {
      return true;
    }

    int codeSizeInBytes = outline.getLirCode().estimatedDexCodeSizeUpperBoundInBytes();
    int estimatedCostInBytes;
    if (outlinerOptions.costInBytesForTesting >= 0) {
      estimatedCostInBytes = outlinerOptions.costInBytesForTesting + codeSizeInBytes;
    } else {
      // Estimate the cost of adding a new synthetic class.
      int estimatedClassDataCostInBytes = 4;
      int estimatedClassDefCostInBytes = 32;
      int estimatedClassNameCostInBytes =
          1 + synthesizingContext.getHolderType().getDescriptor().content.length;
      int estimatedClassRefCostInBytes = 4;
      int estimatedClassCostInBytes =
          estimatedClassDataCostInBytes
              + estimatedClassDefCostInBytes
              + estimatedClassNameCostInBytes
              + estimatedClassRefCostInBytes;

      // Estimate the cost of adding a new method on an existing class.
      int estimatedMethodCodeItemCostInBytes = 16;
      int estimatedMethodDataCostInBytes = 4;
      int estimatedMethodReferenceCostInBytes = 8;
      int estimatedMethodCostInBytes =
          estimatedMethodCodeItemCostInBytes
              + estimatedMethodDataCostInBytes
              + estimatedMethodReferenceCostInBytes
              + codeSizeInBytes;

      // Estimate total cost. Divide class cost by 50 since we allow class merging.
      estimatedCostInBytes =
          Math.round(estimatedClassCostInBytes / 50f) + estimatedMethodCostInBytes;
    }

    // Estimate the savings from this outline.
    int estimatedSavingsInBytes = 0;
    for (Outline outlineOrMergedOutline : outline.getAllOutlines()) {
      for (Multiset.Entry<DexMethod> entry : outlineOrMergedOutline.getUsers().entrySet()) {
        // For each call we save the outlined instructions at the cost of an invoke + return.
        int estimatedSavingsForUser = codeSizeInBytes - (DexInvokeStatic.SIZE + DexReturn.SIZE);
        if (entry.getElement().getReturnType().isWideType()) {
          estimatedSavingsForUser -= DexConstWide16.SIZE;
        } else if (!entry.getElement().getReturnType().isVoidType()) {
          estimatedSavingsForUser -= DexConst4.SIZE;
        }
        estimatedSavingsInBytes += estimatedSavingsForUser * entry.getCount();
        if (estimatedSavingsInBytes > estimatedCostInBytes) {
          return true;
        }
      }
    }
    return false;
  }

  private void processMethods(Collection<Outline> outlines, ExecutorService executorService)
      throws ExecutionException {
    ProgramMethodMap<Outline> methodsToReprocess = getMethodsToReprocess(outlines);
    OutlineMarkerRewriter rewriter = new OutlineMarkerRewriter(appView);
    ThreadUtils.processMap(
        methodsToReprocess,
        (method, outline) -> {
          assert method.getDefinition().hasCode();
          assert method.getDefinition().getCode().isLirCode();
          LirCode<Integer> lirCode = method.getDefinition().getCode().asLirCode();
          if (outline != null) {
            rewriter.processOutlineMethod(method, lirCode, outline);
          } else {
            rewriter.processMethodWithOutlineMarkers(method, lirCode);
          }
        },
        appView.options().getThreadingModule(),
        executorService);
  }

  private ProgramMethodMap<Outline> getMethodsToReprocess(Collection<Outline> outlines) {
    ProgramMethodMap<Outline> methodsToReprocess = ProgramMethodMap.create();
    Set<DexMethod> seenUsers = Sets.newIdentityHashSet();
    for (Outline outline : outlines) {
      for (Outline outlineOrMergedOutline : outline.getAllOutlines()) {
        for (DexMethod user : outlineOrMergedOutline.getUsers().elementSet()) {
          if (seenUsers.add(user)) {
            ProgramMethod methodToReprocess = appView.definitionFor(user).asProgramMethod();
            methodsToReprocess.put(methodToReprocess, null);
          }
        }
      }
      if (outline.getMaterializedOutlineMethod() != null) {
        methodsToReprocess.put(outline.getMaterializedOutlineMethod(), outline);
      }
    }
    return methodsToReprocess;
  }

  private boolean supplyOutlineConsumerForTesting(Collection<Outline> outlines) {
    Consumer<Collection<Outline>> consumer = outlinerOptions.outlineConsumerForTesting;
    if (consumer != null) {
      consumer.accept(outlines);
    }
    return true;
  }
}
