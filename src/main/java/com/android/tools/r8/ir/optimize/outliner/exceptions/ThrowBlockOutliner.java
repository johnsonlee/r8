// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.outliner.exceptions;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.contexts.CompilationContext.ProcessorContext;
import com.android.tools.r8.dex.code.DexConst4;
import com.android.tools.r8.dex.code.DexConstWide16;
import com.android.tools.r8.dex.code.DexInvokeStatic;
import com.android.tools.r8.dex.code.DexReturn;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.lightir.LirCode;
import com.android.tools.r8.lightir.LirConstant;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.collections.ProgramMethodMap;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class ThrowBlockOutliner {

  private final AppView<?> appView;
  private final ThrowBlockOutlinerOptions outlinerOptions;

  // Scans IR code during IR conversion. Responsible for computing candidate outlines.
  private ThrowBlockOutlinerScanner scanner;

  private ThrowBlockOutliner(AppView<?> appView) {
    this.appView = appView;
    this.outlinerOptions = appView.options().getThrowBlockOutlinerOptions();
    this.scanner = new ThrowBlockOutlinerScanner(appView);
  }

  public static ThrowBlockOutliner create(AppView<?> appView) {
    return appView.options().getThrowBlockOutlinerOptions().isEnabled(appView)
        ? new ThrowBlockOutliner(appView)
        : null;
  }

  public void scan(IRCode code) {
    // Notify the scanner.
    if (scanner != null) {
      scanner.run(code);
    }
  }

  public void tearDownScanner(
      Map<DexMethod, DexMethod> forcefullyMovedLambdaMethods, ExecutorService executorService)
      throws ExecutionException {
    // Unset the scanner, which is responsible for computing outline candidates.
    assert scanner != null;
    Collection<ThrowBlockOutline> outlines = scanner.getOutlines();
    scanner = null;

    // Create outlines.
    updateOutlineUsers(outlines, forcefullyMovedLambdaMethods);
    materializeOutlines(outlines, executorService);
    assert supplyOutlineConsumerForTesting(outlines);

    // Convert LIR to DEX.
    processMethods(outlines, executorService);
    appView.unsetThrowBlockOutliner();
  }

  private void updateOutlineUsers(
      Collection<ThrowBlockOutline> outlines,
      Map<DexMethod, DexMethod> forcefullyMovedLambdaMethods) {
    if (forcefullyMovedLambdaMethods.isEmpty()) {
      return;
    }
    for (ThrowBlockOutline outline : outlines) {
      outline.updateUsers(forcefullyMovedLambdaMethods);
    }
  }

  private void materializeOutlines(
      Collection<ThrowBlockOutline> outlines, ExecutorService executorService)
      throws ExecutionException {
    // Find the outlines that we need to synthesize from each method.
    ProgramMethodMap<List<ThrowBlockOutline>> synthesizingContexts = ProgramMethodMap.create();
    for (ThrowBlockOutline outline : outlines) {
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
          Reference2IntMap<ThrowBlockOutline> outlineConstantPoolIndices =
              new Reference2IntOpenHashMap<>();
          for (int i = 0; i < constantPool.length; i++) {
            LirConstant constant = constantPool[i];
            if (constant instanceof ThrowBlockOutline) {
              outlineConstantPoolIndices.put((ThrowBlockOutline) constant, i);
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
          for (ThrowBlockOutline outline : outlinesFromSynthesizingContext) {
            outline.materialize(appView, methodProcessingContext);
          }
        },
        appView.options().getThreadingModule(),
        executorService);
  }

  private boolean shouldMaterializeOutline(
      ThrowBlockOutline outline, ProgramMethod synthesizingContext) {
    Predicate<ThrowBlockOutline> outlineStrategyForTesting =
        outlinerOptions.outlineStrategyForTesting;
    if (outlineStrategyForTesting != null) {
      return outlineStrategyForTesting.test(outline);
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
    for (Multiset.Entry<DexMethod> entry : outline.getUsers().entrySet()) {
      // For each call we save the outlined instructions at the cost of an invoke + return.
      int estimatedSavingsForUser = codeSizeInBytes - 2 * (DexInvokeStatic.SIZE + DexReturn.SIZE);
      if (entry.getElement().getReturnType().isWideType()) {
        estimatedSavingsForUser -= 2 * DexConstWide16.SIZE;
      } else if (!entry.getElement().getReturnType().isVoidType()) {
        estimatedSavingsForUser -= 2 * DexConst4.SIZE;
      }
      estimatedSavingsInBytes += estimatedSavingsForUser * entry.getCount();
      if (estimatedSavingsInBytes > estimatedCostInBytes) {
        return true;
      }
    }
    return false;
  }

  private void processMethods(
      Collection<ThrowBlockOutline> outlines, ExecutorService executorService)
      throws ExecutionException {
    ProgramMethodMap<ThrowBlockOutline> methodsToReprocess = getMethodsToReprocess(outlines);
    ThrowBlockOutlineMarkerRewriter rewriter = new ThrowBlockOutlineMarkerRewriter(appView);
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

  private ProgramMethodMap<ThrowBlockOutline> getMethodsToReprocess(
      Collection<ThrowBlockOutline> outlines) {
    ProgramMethodMap<ThrowBlockOutline> methodsToReprocess = ProgramMethodMap.create();
    Set<DexMethod> seenUsers = Sets.newIdentityHashSet();
    for (ThrowBlockOutline outline : outlines) {
      for (DexMethod user : outline.getUsers()) {
        if (seenUsers.add(user)) {
          ProgramMethod methodToReprocess = appView.definitionFor(user).asProgramMethod();
          methodsToReprocess.put(methodToReprocess, null);
        }
      }
      if (outline.getMaterializedOutlineMethod() != null) {
        methodsToReprocess.put(outline.getMaterializedOutlineMethod(), outline);
      }
    }
    return methodsToReprocess;
  }

  private boolean supplyOutlineConsumerForTesting(Collection<ThrowBlockOutline> outlines) {
    Consumer<Collection<ThrowBlockOutline>> consumer = outlinerOptions.outlineConsumerForTesting;
    if (consumer != null) {
      consumer.accept(outlines);
    }
    return true;
  }
}
